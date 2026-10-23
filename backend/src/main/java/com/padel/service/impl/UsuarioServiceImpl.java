package com.padel.service.impl;

import com.padel.config.AuthProperties;
import com.padel.config.LimitesProperties;
import com.padel.domain.entity.TokenUsuario;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoTokenUsuario;
import com.padel.dto.request.CambiarPasswordRequest;
import com.padel.dto.request.CrearStaffRequest;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.ResetearPasswordRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.DemasiadosIntentosException;
import com.padel.exception.EmailDuplicadoException;
import com.padel.exception.EmailNoVerificadoException;
import com.padel.exception.PasswordIncorrectoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.TokenInvalidoException;
import com.padel.mapper.UsuarioMapper;
import com.padel.repository.TokenUsuarioRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.JwtUtil;
import com.padel.security.LimitadorDePedidos;
import com.padel.security.MensajesLimite;
import com.padel.security.RevocacionTokens;
import com.padel.service.EmailService;
import com.padel.service.UsuarioService;
import com.padel.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioServiceImpl implements UsuarioService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenUsuarioRepository tokenUsuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UsuarioMapper usuarioMapper;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final LimitadorDePedidos limitador;
    private final RevocacionTokens revocacionTokens;
    private final LimitesProperties limites;
    private final AuthProperties authProperties;

    @Override
    @Transactional
    public UsuarioResponse registrar(CrearUsuarioRequest request) {
        String email = EmailUtil.normalizar(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailDuplicadoException("Ese email ya tiene una cuenta. Iniciá sesión o recuperá tu contraseña.");
        }

        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setEmail(email);
        usuario.setTelefono(textoOpcional(request.telefono()));
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setActivo(true);
        // El registro público siempre crea JUGADOR: nadie se auto-asigna ADMIN/RECEPCIONISTA.
        usuario.setRol(RolUsuario.JUGADOR);
        usuario.setEmailVerificado(!authProperties.isVerificacionEmailObligatoria());

        Usuario guardado = usuarioRepository.save(usuario);
        if (guardado.isEmailVerificado()) {
            emailService.enviarBienvenida(guardado);
        } else {
            enviarVerificacion(guardado);
        }
        log.info("Usuario ID {} registrado (email verificado: {})", guardado.getId(), guardado.isEmailVerificado());
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = EmailUtil.normalizar(request.email());
        String claveIntentos = "login-fallido:" + email;
        LimitesProperties.Regla regla = limites.getIntentosFallidosPorCuenta();

        // Bloqueo temporal de la cuenta tras varios intentos fallidos (antes se podía probar contraseñas sin límite).
        if (limites.isHabilitado()) {
            long espera = limitador.segundosBloqueado(claveIntentos, regla.getMaximo());
            if (espera > 0) {
                throw new DemasiadosIntentosException("Demasiados intentos fallidos para esta cuenta. Probá de nuevo en "
                        + MensajesLimite.duracion(espera) + " o recuperá tu contraseña.", espera);
            }
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException e) {
            if (limites.isHabilitado()) {
                limitador.consumir(claveIntentos, regla.getMaximo(), regla.getVentana());
            }
            throw e;
        }
        limitador.reiniciar(claveIntentos);

        Usuario usuario = buscarUsuario(email);
        // Se chequea después de validar la contraseña: sin ella no se revela si la cuenta existe o no está verificada.
        if (!usuario.isEmailVerificado()) {
            throw new EmailNoVerificadoException("Confirmá tu email para iniciar sesión. Revisá tu correo o pedí un nuevo enlace.");
        }
        return new LoginResponse(jwtUtil.generarToken(usuario), usuarioMapper.toResponse(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPerfilPorEmail(String email) {
        return usuarioMapper.toResponse(buscarUsuario(EmailUtil.normalizar(email)));
    }

    @Override
    @Transactional
    public void cambiarPassword(String email, CambiarPasswordRequest request) {
        Usuario usuario = buscarUsuario(EmailUtil.normalizar(email));
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new PasswordIncorrectoException("La contraseña actual no es correcta.");
        }
        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPasswordHash())) {
            throw new DatosInvalidosException("La nueva contraseña tiene que ser distinta de la actual.");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        // Cierra todas las sesiones abiertas (incluida la actual): un token robado deja de servir.
        usuario.invalidarSesiones();
        usuarioRepository.save(usuario);
        tokenUsuarioRepository.invalidarPendientes(usuario.getId(), TipoTokenUsuario.RECUPERACION_PASSWORD);
        emailService.enviarConfirmacionCambioPassword(usuario);
    }

    @Override
    @Transactional
    public void solicitarRecuperacionPassword(String email) {
        // No revela si el email existe: el controller responde siempre lo mismo.
        usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .filter(Usuario::isActivo)
                .filter(this::dentroDelLimiteDeEmails)
                .ifPresent(usuario -> {
                    tokenUsuarioRepository.invalidarPendientes(usuario.getId(), TipoTokenUsuario.RECUPERACION_PASSWORD);
                    String tokenPlano = crearToken(usuario, TipoTokenUsuario.RECUPERACION_PASSWORD,
                            authProperties.getHorasValidezRecuperacion());
                    emailService.enviarRecuperacionPassword(usuario, tokenPlano);
                });
    }

    @Override
    @Transactional
    public void resetearPassword(ResetearPasswordRequest request) {
        TokenUsuario token = tokenUsuarioRepository
                .findByTokenHashAndTipoAndUsadoFalse(hashToken(request.token()), TipoTokenUsuario.RECUPERACION_PASSWORD)
                .filter(t -> t.vigenteAl(LocalDateTime.now()))
                .orElseThrow(() -> new TokenInvalidoException("El enlace de recuperación es inválido o expiró."));

        Usuario usuario = token.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuario.invalidarSesiones();
        // Recibir el enlace en la casilla demuestra que el email es suyo.
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);

        token.setUsado(true);
        tokenUsuarioRepository.save(token);
        tokenUsuarioRepository.invalidarPendientes(usuario.getId(), TipoTokenUsuario.RECUPERACION_PASSWORD);
        limitador.reiniciar("login-fallido:" + usuario.getEmail());

        emailService.enviarConfirmacionCambioPassword(usuario);
    }

    @Override
    @Transactional
    public void verificarEmail(String tokenPlano) {
        TokenUsuario token = tokenUsuarioRepository
                .findByTokenHashAndTipoAndUsadoFalse(hashToken(tokenPlano), TipoTokenUsuario.VERIFICACION_EMAIL)
                .filter(t -> t.vigenteAl(LocalDateTime.now()))
                .orElseThrow(() -> new TokenInvalidoException("El enlace de confirmación es inválido o expiró. Pedí uno nuevo desde el inicio de sesión."));

        Usuario usuario = token.getUsuario();
        token.setUsado(true);
        tokenUsuarioRepository.save(token);
        if (!usuario.isEmailVerificado()) {
            usuario.setEmailVerificado(true);
            usuarioRepository.save(usuario);
            emailService.enviarBienvenida(usuario);
        }
    }

    @Override
    @Transactional
    public void reenviarVerificacion(String email) {
        usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .filter(u -> u.isActivo() && !u.isEmailVerificado())
                .filter(this::dentroDelLimiteDeEmails)
                .ifPresent(this::enviarVerificacion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarPorRol(RolUsuario rol) {
        return usuarioRepository.findByRol(rol).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioResponse crearStaff(CrearStaffRequest request) {
        if (request.rol() != RolUsuario.ADMIN && request.rol() != RolUsuario.RECEPCIONISTA) {
            throw new DatosInvalidosException("El rol de un perfil de staff debe ser ADMIN o RECEPCIONISTA");
        }
        String email = EmailUtil.normalizar(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailDuplicadoException("Ese email ya tiene una cuenta.");
        }

        Usuario staff = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .email(email)
                .telefono(textoOpcional(request.telefono()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .activo(true)
                // La crea un administrador: no hace falta que confirme el email para entrar.
                .emailVerificado(true)
                .build();

        Usuario guardado = usuarioRepository.save(staff);
        emailService.enviarBienvenida(guardado);
        log.info("Cuenta de staff ID {} creada con rol {}", guardado.getId(), guardado.getRol());
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    public void logout(String token) {
        // El JWT es stateless: para que deje de servir antes de vencer se lo revoca por su identificador.
        jwtUtil.leer(token).ifPresent(info -> revocacionTokens.revocar(info.jti(), info.expira()));
    }

    private void enviarVerificacion(Usuario usuario) {
        tokenUsuarioRepository.invalidarPendientes(usuario.getId(), TipoTokenUsuario.VERIFICACION_EMAIL);
        String tokenPlano = crearToken(usuario, TipoTokenUsuario.VERIFICACION_EMAIL, authProperties.getHorasValidezVerificacion());
        emailService.enviarVerificacionEmail(usuario, tokenPlano);
    }

    /** Tope de emails (recuperación/verificación) por cuenta: evita usar el sistema para bombardear una casilla. */
    private boolean dentroDelLimiteDeEmails(Usuario usuario) {
        if (!limites.isHabilitado()) {
            return true;
        }
        LimitesProperties.Regla regla = limites.getEmailsPorCuenta();
        boolean permitido = limitador.consumir("emails:" + usuario.getId(), regla.getMaximo(), regla.getVentana()) == 0;
        if (!permitido) {
            log.warn("Se alcanzó el tope de emails de cuenta para el usuario ID {}", usuario.getId());
        }
        return permitido;
    }

    private String crearToken(Usuario usuario, TipoTokenUsuario tipo, int horasValidez) {
        String tokenPlano = generarTokenAleatorio();
        tokenUsuarioRepository.save(TokenUsuario.builder()
                .usuario(usuario)
                .tipo(tipo)
                .tokenHash(hashToken(tokenPlano))
                .expiraEn(LocalDateTime.now().plusHours(horasValidez))
                .usado(false)
                .build());
        return tokenPlano;
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static String generarTokenAleatorio() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String tokenPlano) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenPlano.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    /** Los campos opcionales del formulario llegan como "" cuando se dejan vacíos: se guardan como null. */
    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
