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
import com.padel.exception.TokenInvalidoException;
import com.padel.mapper.UsuarioMapper;
import com.padel.repository.TokenUsuarioRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.JwtUtil;
import com.padel.security.LimitadorEnMemoria;
import com.padel.security.RevocacionTokens;
import com.padel.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TokenUsuarioRepository tokenUsuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UsuarioMapper usuarioMapper;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EmailService emailService;
    @Mock
    private RevocacionTokens revocacionTokens;
    @Spy
    private LimitadorEnMemoria limitador = new LimitadorEnMemoria();
    @Spy
    private LimitesProperties limites = new LimitesProperties();
    @Spy
    private AuthProperties authProperties = new AuthProperties();

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private Usuario jugador;
    private UsuarioResponse respuesta;

    @BeforeEach
    void setUp() {
        jugador = Usuario.builder().id(1L).nombre("Juan").apellido("Perez").email("juan@example.com")
                .passwordHash("hash").rol(RolUsuario.JUGADOR).activo(true).emailVerificado(true).build();
        respuesta = new UsuarioResponse(1L, "Juan", "Perez", "juan@example.com", null, RolUsuario.JUGADOR, true, true);
    }

    // ---------- registro ----------

    @Test
    void registrar_NormalizaElEmailFuerzaJugadorYPideConfirmarElEmail() {
        CrearUsuarioRequest request = new CrearUsuarioRequest("Juan", "Perez", "  Juan@Example.COM ", null, "Cancha-Segura-2026");
        Usuario mapeado = Usuario.builder().nombre("Juan").apellido("Perez").email(request.email()).build();
        when(usuarioRepository.existsByEmail("juan@example.com")).thenReturn(false);
        when(usuarioMapper.toEntity(request)).thenReturn(mapeado);
        when(passwordEncoder.encode("Cancha-Segura-2026")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(respuesta);

        usuarioService.registrar(request);

        assertEquals("juan@example.com", mapeado.getEmail());
        assertEquals(RolUsuario.JUGADOR, mapeado.getRol());
        assertFalse(mapeado.isEmailVerificado());
        verify(tokenUsuarioRepository).save(any(TokenUsuario.class));
        verify(emailService).enviarVerificacionEmail(eq(mapeado), anyString());
        verify(emailService, never()).enviarBienvenida(any());
    }

    @Test
    void registrar_ElTelefonoVacioSeGuardaComoNull() {
        CrearUsuarioRequest request = new CrearUsuarioRequest("Juan", "Perez", "juan@example.com", "  ", "Cancha-Segura-2026");
        Usuario mapeado = Usuario.builder().nombre("Juan").apellido("Perez").email(request.email()).telefono("  ").build();
        when(usuarioRepository.existsByEmail("juan@example.com")).thenReturn(false);
        when(usuarioMapper.toEntity(request)).thenReturn(mapeado);
        when(passwordEncoder.encode("Cancha-Segura-2026")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(respuesta);

        usuarioService.registrar(request);

        assertNull(mapeado.getTelefono());
    }

    @Test
    void registrar_SinVerificacionObligatoriaQuedaActivaYRecibeLaBienvenida() {
        authProperties.setVerificacionEmailObligatoria(false);
        CrearUsuarioRequest request = new CrearUsuarioRequest("Juan", "Perez", "juan@example.com", null, "Cancha-Segura-2026");
        Usuario mapeado = Usuario.builder().nombre("Juan").email("juan@example.com").build();
        when(usuarioMapper.toEntity(request)).thenReturn(mapeado);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.registrar(request);

        assertTrue(mapeado.isEmailVerificado());
        verify(emailService).enviarBienvenida(mapeado);
    }

    @Test
    void registrar_EmailYaRegistradoAunqueCambienLasMayusculas() {
        when(usuarioRepository.existsByEmail("juan@example.com")).thenReturn(true);

        assertThrows(EmailDuplicadoException.class, () -> usuarioService.registrar(
                new CrearUsuarioRequest("Juan", "Perez", "JUAN@example.com", null, "Cancha-Segura-2026")));
        verify(usuarioRepository, never()).save(any());
    }

    // ---------- login ----------

    @Test
    void login_ExitosoDevuelveElTokenYReiniciaLosIntentos() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(jwtUtil.generarToken(jugador)).thenReturn("token");
        when(usuarioMapper.toResponse(jugador)).thenReturn(respuesta);

        LoginResponse login = usuarioService.login(new LoginRequest("Juan@Example.com", "clave"));

        assertEquals("token", login.token());
        verify(limitador).reiniciar("login-fallido:juan@example.com");
    }

    @Test
    void login_TrasCincoIntentosFallidosLaCuentaQuedaBloqueadaTemporalmente() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class, () -> usuarioService.login(new LoginRequest("juan@example.com", "mala")));
        }
        DemasiadosIntentosException bloqueo = assertThrows(DemasiadosIntentosException.class,
                () -> usuarioService.login(new LoginRequest("juan@example.com", "correcta")));

        assertTrue(bloqueo.getSegundosParaReintentar() > 0);
        verify(authenticationManager, times(5)).authenticate(any());
    }

    @Test
    void login_ConLimitesDeshabilitadosNoBloquea() {
        limites.setHabilitado(false);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        for (int i = 0; i < 7; i++) {
            assertThrows(BadCredentialsException.class, () -> usuarioService.login(new LoginRequest("juan@example.com", "mala")));
        }
    }

    @Test
    void login_CuentaSinEmailConfirmadoNoRecibeToken() {
        jugador.setEmailVerificado(false);
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        EmailNoVerificadoException ex = assertThrows(EmailNoVerificadoException.class,
                () -> usuarioService.login(new LoginRequest("juan@example.com", "clave")));

        assertEquals("EMAIL_NO_VERIFICADO", ex.getCodigo());
        verify(jwtUtil, never()).generarToken(any());
    }

    // ---------- contraseñas ----------

    @Test
    void cambiarPassword_InvalidaLasSesionesAbiertas() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(passwordEncoder.matches("actual", "hash")).thenReturn(true);
        when(passwordEncoder.matches("Nueva-Clave-2026", "hash")).thenReturn(false);
        when(passwordEncoder.encode("Nueva-Clave-2026")).thenReturn("hash-nuevo");

        usuarioService.cambiarPassword("juan@example.com", new CambiarPasswordRequest("actual", "Nueva-Clave-2026"));

        assertEquals("hash-nuevo", jugador.getPasswordHash());
        assertEquals(1, jugador.getTokenVersion());
        verify(tokenUsuarioRepository).invalidarPendientes(1L, TipoTokenUsuario.RECUPERACION_PASSWORD);
        verify(emailService).enviarConfirmacionCambioPassword(jugador);
    }

    @Test
    void cambiarPassword_ConLaActualIncorrectaSeRechaza() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThrows(PasswordIncorrectoException.class,
                () -> usuarioService.cambiarPassword("juan@example.com", new CambiarPasswordRequest("mala", "Nueva-Clave-2026")));
        assertEquals(0, jugador.getTokenVersion());
    }

    @Test
    void cambiarPassword_LaNuevaNoPuedeSerIgualALaActual() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));
        when(passwordEncoder.matches(anyString(), eq("hash"))).thenReturn(true);

        assertThrows(DatosInvalidosException.class,
                () -> usuarioService.cambiarPassword("juan@example.com", new CambiarPasswordRequest("Misma-Clave-2026", "Misma-Clave-2026")));
    }

    @Test
    void solicitarRecuperacion_EmiteUnTokenNuevoEInvalidaLosAnteriores() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        usuarioService.solicitarRecuperacionPassword("JUAN@example.com");

        verify(tokenUsuarioRepository).invalidarPendientes(1L, TipoTokenUsuario.RECUPERACION_PASSWORD);
        ArgumentCaptor<TokenUsuario> token = ArgumentCaptor.forClass(TokenUsuario.class);
        verify(tokenUsuarioRepository).save(token.capture());
        assertEquals(TipoTokenUsuario.RECUPERACION_PASSWORD, token.getValue().getTipo());
        verify(emailService).enviarRecuperacionPassword(eq(jugador), anyString());
    }

    @Test
    void solicitarRecuperacion_EmailInexistenteNoHaceNadaNiLoRevela() {
        when(usuarioRepository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        usuarioService.solicitarRecuperacionPassword("nadie@example.com");

        verify(emailService, never()).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    void solicitarRecuperacion_TopeDeEmailsPorCuenta() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador));

        for (int i = 0; i < 5; i++) {
            usuarioService.solicitarRecuperacionPassword("juan@example.com");
        }

        verify(emailService, times(3)).enviarRecuperacionPassword(eq(jugador), anyString());
    }

    @Test
    void resetearPassword_ConTokenVigenteCambiaLaClaveCierraSesionesYVerificaElEmail() {
        jugador.setEmailVerificado(false);
        TokenUsuario token = TokenUsuario.builder().usuario(jugador).tipo(TipoTokenUsuario.RECUPERACION_PASSWORD)
                .expiraEn(LocalDateTime.now().plusMinutes(30)).build();
        when(tokenUsuarioRepository.findByTokenHashAndTipoAndUsadoFalse(anyString(), eq(TipoTokenUsuario.RECUPERACION_PASSWORD)))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Nueva-Clave-2026")).thenReturn("hash-nuevo");

        usuarioService.resetearPassword(new ResetearPasswordRequest("token-plano", "Nueva-Clave-2026"));

        assertEquals("hash-nuevo", jugador.getPasswordHash());
        assertEquals(1, jugador.getTokenVersion());
        assertTrue(jugador.isEmailVerificado());
        assertTrue(token.isUsado());
    }

    @Test
    void resetearPassword_ConTokenVencidoSeRechaza() {
        TokenUsuario vencido = TokenUsuario.builder().usuario(jugador).tipo(TipoTokenUsuario.RECUPERACION_PASSWORD)
                .expiraEn(LocalDateTime.now().minusMinutes(1)).build();
        when(tokenUsuarioRepository.findByTokenHashAndTipoAndUsadoFalse(anyString(), eq(TipoTokenUsuario.RECUPERACION_PASSWORD)))
                .thenReturn(Optional.of(vencido));

        assertThrows(TokenInvalidoException.class,
                () -> usuarioService.resetearPassword(new ResetearPasswordRequest("token-plano", "Nueva-Clave-2026")));
    }

    // ---------- verificación de email ----------

    @Test
    void verificarEmail_ActivaLaCuentaYMandaLaBienvenida() {
        jugador.setEmailVerificado(false);
        TokenUsuario token = TokenUsuario.builder().usuario(jugador).tipo(TipoTokenUsuario.VERIFICACION_EMAIL)
                .expiraEn(LocalDateTime.now().plusHours(1)).build();
        when(tokenUsuarioRepository.findByTokenHashAndTipoAndUsadoFalse(anyString(), eq(TipoTokenUsuario.VERIFICACION_EMAIL)))
                .thenReturn(Optional.of(token));

        usuarioService.verificarEmail("token-plano");

        assertTrue(jugador.isEmailVerificado());
        assertTrue(token.isUsado());
        verify(emailService).enviarBienvenida(jugador);
    }

    @Test
    void verificarEmail_UnTokenDeRecuperacionNoSirveParaVerificar() {
        when(tokenUsuarioRepository.findByTokenHashAndTipoAndUsadoFalse(anyString(), eq(TipoTokenUsuario.VERIFICACION_EMAIL)))
                .thenReturn(Optional.empty());

        assertThrows(TokenInvalidoException.class, () -> usuarioService.verificarEmail("token-de-recuperacion"));
    }

    @Test
    void reenviarVerificacion_SoloParaCuentasSinConfirmar() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(jugador)); // ya verificada

        usuarioService.reenviarVerificacion("juan@example.com");

        verify(emailService, never()).enviarVerificacionEmail(any(), anyString());
    }

    // ---------- staff y sesión ----------

    @Test
    void crearStaff_RechazaRolJugador() {
        assertThrows(DatosInvalidosException.class, () -> usuarioService.crearStaff(new CrearStaffRequest(
                "Ana", "Lopez", "ana@example.com", null, "Cancha-Segura-2026", RolUsuario.JUGADOR)));
    }

    @Test
    void crearStaff_QuedaConElEmailVerificado() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.crearStaff(new CrearStaffRequest("Ana", "Lopez", "Ana@Example.com", null, "Cancha-Segura-2026", RolUsuario.RECEPCIONISTA));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertTrue(captor.getValue().isEmailVerificado());
        assertEquals("ana@example.com", captor.getValue().getEmail());
    }

    @Test
    void logout_RevocaElTokenUsado() {
        Instant expira = Instant.now().plusSeconds(3600);
        when(jwtUtil.leer("token")).thenReturn(Optional.of(new JwtUtil.TokenInfo(1L, 0, "jti-1", expira)));

        usuarioService.logout("token");

        verify(revocacionTokens).revocar("jti-1", expira);
    }
}
