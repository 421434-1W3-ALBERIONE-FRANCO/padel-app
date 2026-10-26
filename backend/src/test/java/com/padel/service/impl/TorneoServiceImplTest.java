package com.padel.service.impl;

import com.padel.domain.entity.InscripcionTorneo;
import com.padel.domain.entity.PartidoTorneo;
import com.padel.domain.entity.Torneo;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoInscripcion;
import com.padel.domain.enums.EstadoPartido;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.FormatoTorneo;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.InscripcionTorneoRequest;
import com.padel.dto.request.ResultadoPartidoRequest;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.mapper.InscripcionTorneoMapper;
import com.padel.mapper.PartidoTorneoMapper;
import com.padel.mapper.TorneoMapper;
import com.padel.repository.InscripcionTorneoRepository;
import com.padel.repository.PartidoTorneoRepository;
import com.padel.repository.RankingJugadorRepository;
import com.padel.repository.TorneoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.util.FechaHoraUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TorneoServiceImplTest {

    @Mock
    private TorneoRepository torneoRepository;
    @Mock
    private InscripcionTorneoRepository inscripcionTorneoRepository;
    @Mock
    private PartidoTorneoRepository partidoTorneoRepository;
    @Mock
    private RankingJugadorRepository rankingJugadorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TorneoMapper torneoMapper;
    @Mock
    private InscripcionTorneoMapper inscripcionTorneoMapper;
    @Mock
    private PartidoTorneoMapper partidoTorneoMapper;
    @Mock
    private FechaHoraUtil fechaHoraUtil;
    @Mock
    private com.padel.service.NotificacionInAppService notificacionInAppService;

    @InjectMocks
    private TorneoServiceImpl torneoService;

    private Usuario ana;
    private Usuario beto;
    private Usuario carla;
    private Usuario dani;
    private Torneo torneo;

    @BeforeEach
    void setUp() {
        ana = jugador(1L, "ana@test.com");
        beto = jugador(2L, "beto@test.com");
        carla = jugador(3L, "carla@test.com");
        dani = jugador(4L, "dani@test.com");
        torneo = Torneo.builder()
                .id(10L)
                .nombre("Liga de Primavera")
                .formato(FormatoTorneo.LIGA_TODOS_CONTRA_TODOS)
                .categoria(CategoriaJugador.CUARTA)
                .maxParejas(8)
                .estado(EstadoTorneo.INSCRIPCION_ABIERTA)
                .build();
    }

    private Usuario jugador(Long id, String email) {
        return Usuario.builder().id(id).nombre("J" + id).apellido("Test").email(email).rol(RolUsuario.JUGADOR).build();
    }

    private InscripcionTorneo inscripcion(Long id, Usuario j1, Usuario j2, EstadoInscripcion estado) {
        return InscripcionTorneo.builder().id(id).torneo(torneo).jugador1(j1).jugador2(j2).estado(estado).build();
    }

    @Test
    void inscribirPareja_CreaUnaInvitacionPendienteYAvisaAlCompanero() {
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(usuarioRepository.findByEmail("dani@test.com")).thenReturn(Optional.of(dani));
        when(inscripcionTorneoRepository.save(any(InscripcionTorneo.class))).thenAnswer(inv -> inv.getArgument(0));

        torneoService.inscribirPareja(10L, new InscripcionTorneoRequest("DANI@test.com"), "carla@test.com");

        org.mockito.ArgumentCaptor<InscripcionTorneo> captor = org.mockito.ArgumentCaptor.forClass(InscripcionTorneo.class);
        verify(inscripcionTorneoRepository).save(captor.capture());
        assertEquals(EstadoInscripcion.PENDIENTE_ACEPTACION, captor.getValue().getEstado());
        verify(notificacionInAppService).crear(eq(dani), eq(com.padel.domain.enums.TipoNotificacion.INVITACION_TORNEO),
                any(), any(), eq(10L));
    }

    @Test
    void inscribirPareja_ShouldThrow_WhenCompaneroYaJuegaEnOtraPareja_SinRevelarSuEmail() {
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(usuarioRepository.findByEmail("beto@test.com")).thenReturn(Optional.of(beto));
        when(inscripcionTorneoRepository.findByTorneoIdAndEstadoIn(eq(10L), any()))
                .thenReturn(List.of(inscripcion(1L, ana, beto, EstadoInscripcion.CONFIRMADA)));

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class, () ->
                torneoService.inscribirPareja(10L, new InscripcionTorneoRequest("beto@test.com"), "carla@test.com"));

        assertFalse(ex.getMessage().contains("beto@test.com"));
        verify(inscripcionTorneoRepository, never()).save(any());
    }

    @Test
    void inscribirPareja_EmailInexistente_NoRepiteElEmailEnElError() {
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(usuarioRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class, () ->
                torneoService.inscribirPareja(10L, new InscripcionTorneoRequest("nadie@test.com"), "carla@test.com"));
        assertFalse(ex.getMessage().contains("nadie@test.com"));
    }

    @Test
    void inscribirPareja_NoPuedeInvitarDosVecesEnElMismoTorneo() {
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(usuarioRepository.findByEmail("dani@test.com")).thenReturn(Optional.of(dani));
        when(inscripcionTorneoRepository.findByTorneoIdAndEstadoIn(eq(10L), any()))
                .thenReturn(List.of(inscripcion(1L, carla, ana, EstadoInscripcion.PENDIENTE_ACEPTACION)));

        assertThrows(DatosInvalidosException.class, () ->
                torneoService.inscribirPareja(10L, new InscripcionTorneoRequest("dani@test.com"), "carla@test.com"));
    }

    @Test
    void aceptarInvitacion_ConfirmaLaParejaYCancelaLasOtrasInvitacionesPendientes() {
        InscripcionTorneo invitacion = inscripcion(5L, carla, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        InscripcionTorneo otra = inscripcion(6L, ana, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        when(torneoRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("dani@test.com")).thenReturn(Optional.of(dani));
        when(inscripcionTorneoRepository.findByIdAndTorneoId(5L, 10L)).thenReturn(Optional.of(invitacion));
        when(inscripcionTorneoRepository.findByTorneoIdAndEstado(10L, EstadoInscripcion.CONFIRMADA)).thenReturn(List.of());
        when(inscripcionTorneoRepository.findByTorneoIdAndEstado(10L, EstadoInscripcion.PENDIENTE_ACEPTACION)).thenReturn(List.of(invitacion, otra));

        torneoService.aceptarInvitacion(10L, 5L, "dani@test.com");

        assertEquals(EstadoInscripcion.CONFIRMADA, invitacion.getEstado());
        assertEquals(EstadoInscripcion.CANCELADA, otra.getEstado());
        verify(notificacionInAppService).crear(eq(carla), eq(com.padel.domain.enums.TipoNotificacion.INVITACION_TORNEO_RESPONDIDA),
                any(), any(), eq(10L));
    }

    @Test
    void aceptarInvitacion_SoloLaPuedeResponderElInvitado() {
        InscripcionTorneo invitacion = inscripcion(5L, carla, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        when(torneoRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(inscripcionTorneoRepository.findByIdAndTorneoId(5L, 10L)).thenReturn(Optional.of(invitacion));

        assertThrows(com.padel.exception.ResourceNotFoundException.class,
                () -> torneoService.aceptarInvitacion(10L, 5L, "carla@test.com"));
        assertEquals(EstadoInscripcion.PENDIENTE_ACEPTACION, invitacion.getEstado());
    }

    @Test
    void aceptarInvitacion_ConElCupoCompletoSeRechaza() {
        torneo.setMaxParejas(1);
        InscripcionTorneo invitacion = inscripcion(5L, carla, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        when(torneoRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("dani@test.com")).thenReturn(Optional.of(dani));
        when(inscripcionTorneoRepository.findByIdAndTorneoId(5L, 10L)).thenReturn(Optional.of(invitacion));
        when(inscripcionTorneoRepository.findByTorneoIdAndEstado(10L, EstadoInscripcion.CONFIRMADA))
                .thenReturn(List.of(inscripcion(1L, ana, beto, EstadoInscripcion.CONFIRMADA)));

        assertThrows(com.padel.exception.CupoCompletoException.class,
                () -> torneoService.aceptarInvitacion(10L, 5L, "dani@test.com"));
    }

    @Test
    void rechazarInvitacion_LaMarcaComoRechazada() {
        InscripcionTorneo invitacion = inscripcion(5L, carla, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("dani@test.com")).thenReturn(Optional.of(dani));
        when(inscripcionTorneoRepository.findByIdAndTorneoId(5L, 10L)).thenReturn(Optional.of(invitacion));

        torneoService.rechazarInvitacion(10L, 5L, "dani@test.com");

        assertEquals(EstadoInscripcion.RECHAZADA, invitacion.getEstado());
    }

    @Test
    void listarInscripciones_UnJugadorVeLasConfirmadasYSusPropiasInvitaciones() {
        InscripcionTorneo confirmada = inscripcion(1L, ana, beto, EstadoInscripcion.CONFIRMADA);
        InscripcionTorneo mia = inscripcion(2L, carla, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        InscripcionTorneo ajena = inscripcion(3L, ana, dani, EstadoInscripcion.PENDIENTE_ACEPTACION);
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(inscripcionTorneoRepository.findByTorneoIdAndEstadoIn(eq(10L), any())).thenReturn(List.of(confirmada, mia, ajena));

        torneoService.listarInscripciones(10L, "carla@test.com");

        verify(inscripcionTorneoMapper).toResponse(confirmada);
        verify(inscripcionTorneoMapper).toResponse(mia);
        verify(inscripcionTorneoMapper, never()).toResponse(ajena);
    }

    @Test
    void cargarResultado_ShouldThrow_WhenTorneoSuspendido() {
        torneo.setEstado(EstadoTorneo.SUSPENDIDO);
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));

        assertThrows(EstadoInvalidoException.class, () ->
                torneoService.cargarResultado(10L, 5L, new ResultadoPartidoRequest(2, 0)));

        verify(partidoTorneoRepository, never()).save(any());
    }

    @Test
    void cargarResultado_FinalizaLaLiga_CuandoSeJugaronTodosLosPartidos() {
        torneo.setEstado(EstadoTorneo.EN_CURSO);
        InscripcionTorneo pareja1 = InscripcionTorneo.builder().id(1L).torneo(torneo).jugador1(ana).jugador2(beto).build();
        InscripcionTorneo pareja2 = InscripcionTorneo.builder().id(2L).torneo(torneo).jugador1(carla).jugador2(dani).build();
        PartidoTorneo unicoPartido = PartidoTorneo.builder()
                .id(5L).torneo(torneo).ronda("Liga").numeroRonda(1)
                .inscripcion1(pareja1).inscripcion2(pareja2)
                .estado(EstadoPartido.PENDIENTE)
                .build();

        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(partidoTorneoRepository.findByIdAndTorneoId(5L, 10L)).thenReturn(Optional.of(unicoPartido));
        when(partidoTorneoRepository.save(unicoPartido)).thenReturn(unicoPartido);
        when(rankingJugadorRepository.findByJugadorIdAndCategoria(any(), any())).thenReturn(Optional.empty());
        when(partidoTorneoRepository.findByTorneoIdOrderByNumeroRondaAscIdAsc(10L)).thenReturn(List.of(unicoPartido));

        torneoService.cargarResultado(10L, 5L, new ResultadoPartidoRequest(2, 1));

        assertEquals(EstadoPartido.FINALIZADO, unicoPartido.getEstado());
        assertEquals(pareja1, unicoPartido.getGanador());
        assertEquals(EstadoTorneo.FINALIZADO, torneo.getEstado());
        verify(torneoRepository).save(torneo);
        verify(rankingJugadorRepository, times(4)).save(any());
    }

    @Test
    void generarSiguienteRonda_ShouldThrow_WhenTorneoSuspendido() {
        torneo.setFormato(FormatoTorneo.ELIMINACION_DIRECTA);
        torneo.setEstado(EstadoTorneo.SUSPENDIDO);
        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));

        assertThrows(EstadoInvalidoException.class, () -> torneoService.generarSiguienteRonda(10L));

        verify(partidoTorneoRepository, never()).saveAll(any());
    }
}
