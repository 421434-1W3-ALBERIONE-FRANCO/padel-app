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

    @Test
    void inscribirPareja_ShouldThrow_WhenCompaneroYaJuegaEnOtraPareja() {
        InscripcionTorneo anaConBeto = InscripcionTorneo.builder().id(1L).torneo(torneo).jugador1(ana).jugador2(beto).build();

        when(torneoRepository.findById(10L)).thenReturn(Optional.of(torneo));
        when(usuarioRepository.findByEmail("carla@test.com")).thenReturn(Optional.of(carla));
        when(usuarioRepository.findByEmail("beto@test.com")).thenReturn(Optional.of(beto));
        when(inscripcionTorneoRepository.countByTorneoIdAndEstado(10L, EstadoInscripcion.CONFIRMADA)).thenReturn(1L);
        when(inscripcionTorneoRepository.findByTorneoIdAndEstado(10L, EstadoInscripcion.CONFIRMADA)).thenReturn(List.of(anaConBeto));

        DatosInvalidosException ex = assertThrows(DatosInvalidosException.class, () ->
                torneoService.inscribirPareja(10L, new InscripcionTorneoRequest("beto@test.com"), "carla@test.com"));

        assertTrue(ex.getMessage().contains("beto@test.com"));
        verify(inscripcionTorneoRepository, never()).save(any());
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
