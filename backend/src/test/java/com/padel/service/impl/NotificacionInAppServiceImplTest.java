package com.padel.service.impl;

import com.padel.domain.entity.Notificacion;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.response.NotificacionResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.NotificacionMapper;
import com.padel.repository.NotificacionRepository;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionInAppServiceImplTest {

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private NotificacionMapper notificacionMapper;

    @InjectMocks
    private NotificacionInAppServiceImpl notificacionInAppService;

    private Usuario usuarioMock;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .email("juan@example.com")
                .rol(RolUsuario.JUGADOR)
                .activo(true)
                .build();
    }

    @Test
    void crear_ShouldSaveNotificacion() {
        notificacionInAppService.crear(usuarioMock, TipoNotificacion.RECORDATORIO_RESERVA, "Titulo", "Mensaje", 5L);

        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test
    void listarMias_ShouldReturnMappedList() {
        Notificacion n = Notificacion.builder().id(1L).usuario(usuarioMock).tipo(TipoNotificacion.RECORDATORIO_RESERVA)
                .titulo("Titulo").mensaje("Mensaje").leida(false).build();
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n));
        NotificacionResponse responseDto = new NotificacionResponse(1L, TipoNotificacion.RECORDATORIO_RESERVA, "Titulo", "Mensaje", false, null, LocalDateTime.now());
        when(notificacionMapper.toResponse(n)).thenReturn(responseDto);

        List<NotificacionResponse> result = notificacionInAppService.listarMias("juan@example.com");

        assertEquals(1, result.size());
    }

    @Test
    void contarNoLeidas_ShouldReturnCount() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(notificacionRepository.countByUsuarioIdAndLeidaFalse(1L)).thenReturn(3L);

        long count = notificacionInAppService.contarNoLeidas("juan@example.com");

        assertEquals(3L, count);
    }

    @Test
    void marcarLeida_ShouldMarkAsRead_WhenOwnedByUser() {
        Notificacion n = Notificacion.builder().id(1L).usuario(usuarioMock).tipo(TipoNotificacion.RECORDATORIO_RESERVA)
                .titulo("Titulo").mensaje("Mensaje").leida(false).build();
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));

        notificacionInAppService.marcarLeida(1L, "juan@example.com");

        assertTrue(n.isLeida());
        assertNotNull(n.getLeidaEn());
        verify(notificacionRepository).save(n);
    }

    @Test
    void marcarLeida_ShouldThrowResourceNotFoundException_WhenNotOwnedByUser() {
        Usuario otroUsuario = Usuario.builder().id(2L).email("otro@example.com").rol(RolUsuario.JUGADOR).build();
        Notificacion n = Notificacion.builder().id(1L).usuario(otroUsuario).tipo(TipoNotificacion.RECORDATORIO_RESERVA)
                .titulo("Titulo").mensaje("Mensaje").leida(false).build();
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(notificacionRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThrows(ResourceNotFoundException.class, () -> notificacionInAppService.marcarLeida(1L, "juan@example.com"));

        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void marcarTodasLeidas_ShouldCallRepositoryUpdate() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));

        notificacionInAppService.marcarTodasLeidas("juan@example.com");

        verify(notificacionRepository).marcarTodasLeidas(eq(1L), any(LocalDateTime.class));
    }
}
