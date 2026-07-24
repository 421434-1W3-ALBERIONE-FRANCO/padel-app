package com.padel.service.impl;

import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearUsuarioRequest;
import com.padel.dto.request.LoginRequest;
import com.padel.dto.response.LoginResponse;
import com.padel.dto.response.UsuarioResponse;
import com.padel.exception.EmailDuplicadoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.UsuarioMapper;
import com.padel.repository.TokenRecuperacionPasswordRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.JwtUtil;
import com.padel.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

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
    private TokenRecuperacionPasswordRepository tokenRecuperacionPasswordRepository;

    @Mock
    private com.padel.service.SesionTrabajoService sesionTrabajoService;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private Usuario usuarioMock;
    private CrearUsuarioRequest crearUsuarioRequest;
    private UsuarioResponse usuarioResponse;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("Perez")
                .email("juan.perez@example.com")
                .telefono("123456789")
                .passwordHash("encodedPassword")
                .rol(RolUsuario.JUGADOR)
                .activo(true)
                .build();

        crearUsuarioRequest = new CrearUsuarioRequest(
                "Juan",
                "Perez",
                "juan.perez@example.com",
                "123456789",
                "password123",
                RolUsuario.JUGADOR
        );

        usuarioResponse = new UsuarioResponse(
                1L,
                "Juan",
                "Perez",
                "juan.perez@example.com",
                "123456789",
                RolUsuario.JUGADOR,
                true
        );
    }

    @Test
    void registrar_ShouldSaveAndReturnResponse_WhenEmailDoesNotExist() {
        // Arrange
        when(usuarioRepository.existsByEmail(crearUsuarioRequest.email())).thenReturn(false);
        when(usuarioMapper.toEntity(crearUsuarioRequest)).thenReturn(usuarioMock);
        when(passwordEncoder.encode(crearUsuarioRequest.password())).thenReturn("encodedPassword");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioMock);
        when(usuarioMapper.toResponse(usuarioMock)).thenReturn(usuarioResponse);

        // Act
        UsuarioResponse result = usuarioService.registrar(crearUsuarioRequest);

        // Assert
        assertNotNull(result);
        assertEquals(crearUsuarioRequest.email(), result.email());
        assertEquals(RolUsuario.JUGADOR, result.rol());
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    @Test
    void registrar_ShouldThrowEmailDuplicadoException_WhenEmailExists() {
        // Arrange
        when(usuarioRepository.existsByEmail(crearUsuarioRequest.email())).thenReturn(true);

        // Act & Assert
        assertThrows(EmailDuplicadoException.class, () -> usuarioService.registrar(crearUsuarioRequest));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void login_ShouldReturnTokenAndProfile_WhenCredentialsAreValid() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("juan.perez@example.com", "password123");
        Authentication authentication = mock(Authentication.class);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(usuarioRepository.findByEmail(loginRequest.email())).thenReturn(Optional.of(usuarioMock));
        when(jwtUtil.generateToken(usuarioMock.getEmail(), usuarioMock.getRol().name())).thenReturn("mockToken123");
        when(usuarioMapper.toResponse(usuarioMock)).thenReturn(usuarioResponse);

        // Act
        LoginResponse result = usuarioService.login(loginRequest);

        // Assert
        assertNotNull(result);
        assertEquals("mockToken123", result.token());
        assertEquals(usuarioResponse.email(), result.usuario().email());
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_ShouldThrowResourceNotFoundException_WhenUserDoesNotExistPostAuth() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("juan.perez@example.com", "password123");
        Authentication authentication = mock(Authentication.class);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(usuarioRepository.findByEmail(loginRequest.email())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> usuarioService.login(loginRequest));
    }

    @Test
    void obtenerPerfilPorEmail_ShouldReturnProfile_WhenUserExists() {
        // Arrange
        String email = "juan.perez@example.com";
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioMock));
        when(usuarioMapper.toResponse(usuarioMock)).thenReturn(usuarioResponse);

        // Act
        UsuarioResponse result = usuarioService.obtenerPerfilPorEmail(email);

        // Assert
        assertNotNull(result);
        assertEquals(email, result.email());
    }

    @Test
    void obtenerPerfilPorEmail_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        // Arrange
        String email = "missing@example.com";
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> usuarioService.obtenerPerfilPorEmail(email));
    }

    @Test
    void cambiarPassword_ShouldUpdateHashAndSendEmail_WhenCurrentPasswordMatches() {
        String email = "juan.perez@example.com";
        com.padel.dto.request.CambiarPasswordRequest request =
                new com.padel.dto.request.CambiarPasswordRequest("actual123", "nueva123456");

        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("actual123", usuarioMock.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("nueva123456")).thenReturn("encodedNueva");

        usuarioService.cambiarPassword(email, request);

        assertEquals("encodedNueva", usuarioMock.getPasswordHash());
        verify(usuarioRepository).save(usuarioMock);
        verify(emailService).enviarConfirmacionCambioPassword(usuarioMock);
    }

    @Test
    void cambiarPassword_ShouldThrowPasswordIncorrectoException_WhenCurrentPasswordDoesNotMatch() {
        String email = "juan.perez@example.com";
        com.padel.dto.request.CambiarPasswordRequest request =
                new com.padel.dto.request.CambiarPasswordRequest("incorrecta", "nueva123456");

        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("incorrecta", usuarioMock.getPasswordHash())).thenReturn(false);

        assertThrows(com.padel.exception.PasswordIncorrectoException.class,
                () -> usuarioService.cambiarPassword(email, request));

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(emailService, never()).enviarConfirmacionCambioPassword(any());
    }

    @Test
    void solicitarRecuperacionPassword_ShouldCreateTokenAndSendEmail_WhenEmailExists() {
        String email = "juan.perez@example.com";
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuarioMock));

        usuarioService.solicitarRecuperacionPassword(email);

        verify(tokenRecuperacionPasswordRepository).save(any(com.padel.domain.entity.TokenRecuperacionPassword.class));
        verify(emailService).enviarRecuperacionPassword(eq(usuarioMock), anyString());
    }

    @Test
    void solicitarRecuperacionPassword_ShouldDoNothing_WhenEmailDoesNotExist() {
        String email = "missing@example.com";
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.empty());

        usuarioService.solicitarRecuperacionPassword(email);

        verify(tokenRecuperacionPasswordRepository, never()).save(any());
        verify(emailService, never()).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    void resetearPassword_ShouldThrowTokenInvalidoException_WhenTokenDoesNotExist() {
        com.padel.dto.request.ResetearPasswordRequest request =
                new com.padel.dto.request.ResetearPasswordRequest("token-invalido", "nueva123456");

        when(tokenRecuperacionPasswordRepository.findByTokenHashAndUsadoFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(com.padel.exception.TokenInvalidoException.class,
                () -> usuarioService.resetearPassword(request));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void resetearPassword_ShouldThrowTokenInvalidoException_WhenTokenExpired() {
        com.padel.dto.request.ResetearPasswordRequest request =
                new com.padel.dto.request.ResetearPasswordRequest("token-expirado", "nueva123456");

        com.padel.domain.entity.TokenRecuperacionPassword tokenExpirado =
                com.padel.domain.entity.TokenRecuperacionPassword.builder()
                        .id(1L)
                        .usuario(usuarioMock)
                        .tokenHash("hash-irrelevante-para-el-test")
                        .expiraEn(java.time.LocalDateTime.now().minusMinutes(1))
                        .usado(false)
                        .build();

        when(tokenRecuperacionPasswordRepository.findByTokenHashAndUsadoFalse(anyString()))
                .thenReturn(Optional.of(tokenExpirado));

        assertThrows(com.padel.exception.TokenInvalidoException.class,
                () -> usuarioService.resetearPassword(request));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crearStaff_ShouldCreateWithRequestedRoleAndSendWelcome() {
        com.padel.dto.request.CrearStaffRequest request = new com.padel.dto.request.CrearStaffRequest(
                "Ana", "Recep", "ana@test.com", "123", "clave123", RolUsuario.RECEPCIONISTA);
        when(usuarioRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("clave123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(usuarioResponse);

        usuarioService.crearStaff(request);

        org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals(RolUsuario.RECEPCIONISTA, captor.getValue().getRol());
        verify(emailService).enviarBienvenida(any(Usuario.class));
    }

    @Test
    void crearStaff_ShouldThrowDatosInvalidos_WhenRolIsJugador() {
        com.padel.dto.request.CrearStaffRequest request = new com.padel.dto.request.CrearStaffRequest(
                "Ana", "Jug", "ana@test.com", "123", "clave123", RolUsuario.JUGADOR);

        assertThrows(com.padel.exception.DatosInvalidosException.class, () -> usuarioService.crearStaff(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crearStaff_ShouldThrowEmailDuplicado_WhenEmailExists() {
        com.padel.dto.request.CrearStaffRequest request = new com.padel.dto.request.CrearStaffRequest(
                "Ana", "Recep", "ana@test.com", "123", "clave123", RolUsuario.RECEPCIONISTA);
        when(usuarioRepository.existsByEmail("ana@test.com")).thenReturn(true);

        assertThrows(EmailDuplicadoException.class, () -> usuarioService.crearStaff(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void resetearPassword_ShouldUpdatePasswordAndMarkTokenUsed_WhenTokenValid() {
        com.padel.dto.request.ResetearPasswordRequest request =
                new com.padel.dto.request.ResetearPasswordRequest("token-valido", "nueva123456");

        com.padel.domain.entity.TokenRecuperacionPassword tokenValido =
                com.padel.domain.entity.TokenRecuperacionPassword.builder()
                        .id(1L)
                        .usuario(usuarioMock)
                        .tokenHash("hash-irrelevante-para-el-test")
                        .expiraEn(java.time.LocalDateTime.now().plusMinutes(30))
                        .usado(false)
                        .build();

        when(tokenRecuperacionPasswordRepository.findByTokenHashAndUsadoFalse(anyString()))
                .thenReturn(Optional.of(tokenValido));
        when(passwordEncoder.encode("nueva123456")).thenReturn("encodedNueva");

        usuarioService.resetearPassword(request);

        assertEquals("encodedNueva", usuarioMock.getPasswordHash());
        assertTrue(tokenValido.isUsado());
        verify(usuarioRepository).save(usuarioMock);
        verify(tokenRecuperacionPasswordRepository).save(tokenValido);
        verify(emailService).enviarConfirmacionCambioPassword(usuarioMock);
    }
}
