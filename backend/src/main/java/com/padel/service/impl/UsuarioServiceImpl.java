package com.padel.service.impl;

import com.padel.domain.entity.TokenRecuperacionPassword;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CambiarPasswordRequest;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.request.ResetearPasswordRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;
import com.padel.exception.EmailDuplicadoException;
import com.padel.exception.PasswordIncorrectoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.TokenInvalidoException;
import com.padel.mapper.UsuarioMapper;
import com.padel.repository.TokenRecuperacionPasswordRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.EmailService;
import com.padel.service.SesionTrabajoService;
import com.padel.service.UsuarioService;
import com.padel.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
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
public class UsuarioServiceImpl implements UsuarioService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacionPasswordRepository tokenRecuperacionPasswordRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UsuarioMapper usuarioMapper;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final SesionTrabajoService sesionTrabajoService;

    @Override
    @Transactional
    public UsuarioResponse registrar(CrearUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailDuplicadoException("El correo electrónico " + request.email() + " ya está registrado.");
        }

        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setActivo(true);
        // El registro público siempre crea JUGADOR: el rol del request se ignora para evitar
        // que un cliente se auto-asigne ADMIN/RECEPCIONISTA vía POST /auth/registro.
        usuario.setRol(RolUsuario.JUGADOR);

        Usuario guardado = usuarioRepository.save(usuario);
        emailService.enviarBienvenida(guardado);
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Ejecuta la autenticación de Spring Security (valida credenciales y estado activo)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.email()));

        // Abrir sesión de trabajo solo para el staff (ADMIN/RECEPCIONISTA), nunca para JUGADOR.
        if (usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA) {
            sesionTrabajoService.abrirSesion(usuario);
        }

        String token = jwtUtil.generateToken(usuario.getEmail(), usuario.getRol().name());
        UsuarioResponse usuarioResponse = usuarioMapper.toResponse(usuario);

        return new LoginResponse(token, usuarioResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPerfilPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional
    public void cambiarPassword(String email, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new PasswordIncorrectoException("La contraseña actual no es correcta.");
        }

        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);
        emailService.enviarConfirmacionCambioPassword(usuario);
    }

    @Override
    @Transactional
    public void solicitarRecuperacionPassword(String email) {
        // No revela si el email existe o no: el controller responde siempre lo mismo,
        // exista o no el usuario, para evitar enumeración de cuentas.
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String tokenPlano = generarTokenAleatorio();
            TokenRecuperacionPassword token = TokenRecuperacionPassword.builder()
                    .usuario(usuario)
                    .tokenHash(hashToken(tokenPlano))
                    .expiraEn(LocalDateTime.now().plusHours(1))
                    .usado(false)
                    .build();
            tokenRecuperacionPasswordRepository.save(token);
            emailService.enviarRecuperacionPassword(usuario, tokenPlano);
        });
    }

    @Override
    @Transactional
    public void resetearPassword(ResetearPasswordRequest request) {
        TokenRecuperacionPassword token = tokenRecuperacionPasswordRepository
                .findByTokenHashAndUsadoFalse(hashToken(request.token()))
                .filter(t -> t.getExpiraEn().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new TokenInvalidoException("El enlace de recuperación es inválido o expiró."));

        Usuario usuario = token.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.save(usuario);

        token.setUsado(true);
        tokenRecuperacionPasswordRepository.save(token);

        emailService.enviarConfirmacionCambioPassword(usuario);
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
    public UsuarioResponse crearStaff(com.padel.dto.request.CrearStaffRequest request) {
        if (request.rol() != RolUsuario.ADMIN && request.rol() != RolUsuario.RECEPCIONISTA) {
            throw new com.padel.exception.DatosInvalidosException("El rol de un perfil de staff debe ser ADMIN o RECEPCIONISTA");
        }
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailDuplicadoException("El correo electrónico " + request.email() + " ya está registrado.");
        }

        Usuario staff = Usuario.builder()
                .nombre(request.nombre())
                .apellido(request.apellido())
                .email(request.email())
                .telefono(request.telefono())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .activo(true)
                .build();

        Usuario guardado = usuarioRepository.save(staff);
        emailService.enviarBienvenida(guardado);
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public void logout(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
        // Cerrar sesión de trabajo solo aplica al staff; para JUGADOR es no-op.
        if (usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA) {
            sesionTrabajoService.cerrarSesion(usuario, false);
        }
    }

    private static String generarTokenAleatorio() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String tokenPlano) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenPlano.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
