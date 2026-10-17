package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.DisponibilidadJugador;
import com.padel.domain.entity.MatchPareja;
import com.padel.domain.entity.MatchSwipe;
import com.padel.domain.entity.PerfilJugador;
import com.padel.domain.entity.RankingJugador;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.BloqueHorario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.PosicionJuego;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.enums.TipoSwipe;
import com.padel.dto.request.ReservaMatchRequest;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.request.SwipeRequest;
import com.padel.dto.response.CandidatoMatchResponse;
import com.padel.dto.response.DisponibilidadResponse;
import com.padel.dto.response.ReservaResponse;
import com.padel.dto.response.SwipeResponse;
import com.padel.dto.response.TurnoSugeridoResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.MatchMapper;
import com.padel.repository.CanchaRepository;
import com.padel.repository.MatchParejaRepository;
import com.padel.repository.MatchSwipeRepository;
import com.padel.repository.PerfilJugadorRepository;
import com.padel.repository.RankingJugadorRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.DisponibilidadService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock
    private PerfilJugadorRepository perfilJugadorRepository;
    @Mock
    private MatchSwipeRepository matchSwipeRepository;
    @Mock
    private MatchParejaRepository matchParejaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RankingJugadorRepository rankingJugadorRepository;
    @Mock
    private CanchaRepository canchaRepository;
    @Mock
    private DisponibilidadService disponibilidadService;
    @Mock
    private ReservaService reservaService;
    @Mock
    private NotificacionInAppService notificacionInAppService;
    @Spy
    private MatchMapper matchMapper = new MatchMapper();

    @InjectMocks
    private MatchServiceImpl matchService;

    private Usuario yo;
    private PerfilJugador miPerfil;

    @BeforeEach
    void setUp() {
        yo = usuario(1L, "Juan", "Perez");
        // Cuarta, drive, juega martes y jueves a la noche y sábado a la mañana.
        miPerfil = perfil(yo, CategoriaJugador.CUARTA, PosicionJuego.DRIVE,
                bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE),
                bloque(DayOfWeek.THURSDAY, BloqueHorario.NOCHE),
                bloque(DayOfWeek.SATURDAY, BloqueHorario.MANANA));
        lenient().when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(yo));
        lenient().when(perfilJugadorRepository.findByUsuarioId(1L)).thenReturn(Optional.of(miPerfil));
    }

    private static Usuario usuario(Long id, String nombre, String apellido) {
        return Usuario.builder().id(id).nombre(nombre).apellido(apellido)
                .email(nombre.toLowerCase() + "@test.com").telefono("351" + id).rol(RolUsuario.JUGADOR).activo(true).build();
    }

    private static DisponibilidadJugador bloque(DayOfWeek dia, BloqueHorario bloque) {
        return new DisponibilidadJugador(dia, bloque);
    }

    private static PerfilJugador perfil(Usuario usuario, CategoriaJugador categoria, PosicionJuego posicion, DisponibilidadJugador... bloques) {
        return PerfilJugador.builder().id(usuario.getId() * 10).usuario(usuario).categoria(categoria).posicion(posicion)
                .visibleEnMatch(true).disponibilidad(new HashSet<>(Arrays.asList(bloques))).build();
    }

    @Test
    void obtenerCandidatos_filtraPorCategoriaDisponibilidadYRespuestasPrevias_yOrdenaPorAfinidad() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");      // cuarta, revés, 3 horarios en común
        Usuario tomas = usuario(3L, "Tomas", "Reyes");       // quinta, drive, 1 en común
        Usuario nico = usuario(4L, "Nicolas", "Peralta");    // segunda: dos categorías de distancia
        Usuario cami = usuario(5L, "Camila", "Suarez");      // sin horarios en común
        Usuario vale = usuario(6L, "Valentina", "Gomez");    // ya le respondí
        PerfilJugador pSofia = perfil(sofia, CategoriaJugador.CUARTA, PosicionJuego.REVES,
                bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE), bloque(DayOfWeek.THURSDAY, BloqueHorario.NOCHE),
                bloque(DayOfWeek.SATURDAY, BloqueHorario.MANANA));
        PerfilJugador pTomas = perfil(tomas, CategoriaJugador.QUINTA, PosicionJuego.DRIVE,
                bloque(DayOfWeek.SATURDAY, BloqueHorario.MANANA), bloque(DayOfWeek.SUNDAY, BloqueHorario.TARDE));
        PerfilJugador pNico = perfil(nico, CategoriaJugador.SEGUNDA, PosicionJuego.REVES,
                bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE));
        PerfilJugador pCami = perfil(cami, CategoriaJugador.CUARTA, PosicionJuego.REVES,
                bloque(DayOfWeek.MONDAY, BloqueHorario.MANANA));
        PerfilJugador pVale = perfil(vale, CategoriaJugador.CUARTA, PosicionJuego.REVES,
                bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE));

        when(matchSwipeRepository.findDestinoIdsByOrigenId(1L)).thenReturn(Set.of(6L));
        when(perfilJugadorRepository.findByVisibleEnMatchTrueAndUsuarioActivoTrue())
                .thenReturn(List.of(miPerfil, pTomas, pNico, pCami, pVale, pSofia));
        when(rankingJugadorRepository.findByJugadorIdIn(anyCollection())).thenReturn(List.of(
                RankingJugador.builder().jugador(sofia).categoria(CategoriaJugador.CUARTA).puntos(340).build(),
                RankingJugador.builder().jugador(sofia).categoria(CategoriaJugador.TERCERA).puntos(12).build()));

        List<CandidatoMatchResponse> candidatos = matchService.obtenerCandidatos("juan@test.com", 20);

        assertEquals(List.of(2L, 3L), candidatos.stream().map(CandidatoMatchResponse::usuarioId).toList());
        CandidatoMatchResponse primera = candidatos.get(0);
        assertEquals("Sofia I.", primera.nombre());
        assertEquals("SI", primera.iniciales());
        assertEquals(340, primera.puntosRanking());
        assertEquals(3, primera.coincidencias().size());
        // 3 de 6 horarios (0.25) + misma categoría (0.3) + drive con revés (0.2) = 75.
        assertEquals(75, primera.afinidad());
        assertEquals(0, candidatos.get(1).puntosRanking());
        assertTrue(primera.afinidad() > candidatos.get(1).afinidad());
    }

    @Test
    void obtenerCandidatos_sinPerfil_lanzaEstadoInvalido() {
        when(perfilJugadorRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThrows(EstadoInvalidoException.class, () -> matchService.obtenerCandidatos("juan@test.com", 20));
    }

    @Test
    void obtenerCandidatos_conPerfilOculto_lanzaEstadoInvalido() {
        miPerfil.setVisibleEnMatch(false);

        assertThrows(EstadoInvalidoException.class, () -> matchService.obtenerCandidatos("juan@test.com", 20));
    }

    @Test
    void registrarSwipe_likeSinReciprocidad_noGeneraMatch() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        PerfilJugador pSofia = perfil(sofia, CategoriaJugador.CUARTA, PosicionJuego.REVES, bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE));
        when(perfilJugadorRepository.findByUsuarioId(2L)).thenReturn(Optional.of(pSofia));
        when(matchSwipeRepository.existsByOrigenIdAndDestinoId(1L, 2L)).thenReturn(false);
        when(matchSwipeRepository.existsByOrigenIdAndDestinoIdAndTipo(2L, 1L, TipoSwipe.LIKE)).thenReturn(false);

        SwipeResponse response = matchService.registrarSwipe(new SwipeRequest(2L, TipoSwipe.LIKE), "juan@test.com");

        assertFalse(response.match());
        assertNull(response.pareja());
        ArgumentCaptor<MatchSwipe> swipe = ArgumentCaptor.forClass(MatchSwipe.class);
        verify(matchSwipeRepository).save(swipe.capture());
        assertEquals(TipoSwipe.LIKE, swipe.getValue().getTipo());
        assertEquals(sofia, swipe.getValue().getDestino());
        verify(matchParejaRepository, never()).save(any());
    }

    @Test
    void registrarSwipe_likeMutuo_creaMatchOrdenadoYAvisaAAmbos() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        PerfilJugador pSofia = perfil(sofia, CategoriaJugador.CUARTA, PosicionJuego.REVES, bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE));
        when(perfilJugadorRepository.findByUsuarioId(2L)).thenReturn(Optional.of(pSofia));
        when(matchSwipeRepository.existsByOrigenIdAndDestinoId(1L, 2L)).thenReturn(false);
        when(matchSwipeRepository.existsByOrigenIdAndDestinoIdAndTipo(2L, 1L, TipoSwipe.LIKE)).thenReturn(true);
        when(matchParejaRepository.findByJugador1IdAndJugador2Id(1L, 2L)).thenReturn(Optional.empty());
        when(matchParejaRepository.save(any(MatchPareja.class))).thenAnswer(inv -> {
            MatchPareja m = inv.getArgument(0);
            m.setId(77L);
            m.setCreatedAt(LocalDateTime.now());
            return m;
        });

        SwipeResponse response = matchService.registrarSwipe(new SwipeRequest(2L, TipoSwipe.LIKE), "juan@test.com");

        assertTrue(response.match());
        assertEquals(77L, response.pareja().id());
        assertEquals("Sofia Ibarra", response.pareja().nombre());
        assertEquals("sofia@test.com", response.pareja().email());
        assertEquals(1, response.pareja().coincidencias().size());
        ArgumentCaptor<MatchPareja> match = ArgumentCaptor.forClass(MatchPareja.class);
        verify(matchParejaRepository).save(match.capture());
        assertEquals(1L, match.getValue().getJugador1().getId());
        assertEquals(2L, match.getValue().getJugador2().getId());
        verify(notificacionInAppService).crear(eq(yo), eq(TipoNotificacion.MATCH_NUEVO), any(), any(), eq(77L));
        verify(notificacionInAppService).crear(eq(sofia), eq(TipoNotificacion.MATCH_NUEVO), any(), any(), eq(77L));
    }

    @Test
    void registrarSwipe_aVosMismo_lanzaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class,
                () -> matchService.registrarSwipe(new SwipeRequest(1L, TipoSwipe.LIKE), "juan@test.com"));
        verify(matchSwipeRepository, never()).save(any());
    }

    @Test
    void registrarSwipe_repetido_lanzaEstadoInvalido() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        when(perfilJugadorRepository.findByUsuarioId(2L)).thenReturn(Optional.of(
                perfil(sofia, CategoriaJugador.CUARTA, PosicionJuego.REVES, bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE))));
        when(matchSwipeRepository.existsByOrigenIdAndDestinoId(1L, 2L)).thenReturn(true);

        assertThrows(EstadoInvalidoException.class,
                () -> matchService.registrarSwipe(new SwipeRequest(2L, TipoSwipe.PASS), "juan@test.com"));
        verify(matchSwipeRepository, never()).save(any());
    }

    @Test
    void registrarSwipe_aJugadorOculto_lanzaNotFound() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        PerfilJugador oculto = perfil(sofia, CategoriaJugador.CUARTA, PosicionJuego.REVES, bloque(DayOfWeek.TUESDAY, BloqueHorario.NOCHE));
        oculto.setVisibleEnMatch(false);
        when(perfilJugadorRepository.findByUsuarioId(2L)).thenReturn(Optional.of(oculto));

        assertThrows(ResourceNotFoundException.class,
                () -> matchService.registrarSwipe(new SwipeRequest(2L, TipoSwipe.LIKE), "juan@test.com"));
    }

    @Test
    void obtenerMisParejas_completaUnMatchPendientePorLikesSimultaneos() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        when(matchSwipeRepository.findLikesMutuos(1L)).thenReturn(List.of(2L));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(sofia));
        when(matchParejaRepository.findByJugador1IdAndJugador2Id(1L, 2L)).thenReturn(Optional.empty());
        when(matchParejaRepository.save(any(MatchPareja.class))).thenAnswer(inv -> {
            MatchPareja m = inv.getArgument(0);
            m.setId(5L);
            return m;
        });
        MatchPareja existente = MatchPareja.builder().id(5L).jugador1(yo).jugador2(sofia).build();
        when(matchParejaRepository.findDelUsuario(1L)).thenReturn(List.of(existente));
        when(perfilJugadorRepository.findByUsuarioIdIn(anyCollection())).thenReturn(List.of(miPerfil));

        var parejas = matchService.obtenerMisParejas("juan@test.com");

        assertEquals(1, parejas.size());
        assertEquals("Sofia Ibarra", parejas.get(0).nombre());
        assertNull(parejas.get(0).categoria());
        assertTrue(parejas.get(0).coincidencias().isEmpty());
        verify(matchParejaRepository).save(any(MatchPareja.class));
    }

    @Test
    void sugerirTurnos_soloDevuelveTurnosLibresEnHorariosCompartidos() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        LocalDate hoy = LocalDate.now();
        // Ambos pueden el día de hoy a la noche; el compañero además a la tarde (el usuario nunca juega de tarde).
        miPerfil.getDisponibilidad().add(bloque(hoy.getDayOfWeek(), BloqueHorario.NOCHE));
        PerfilJugador pSofia = perfil(sofia, CategoriaJugador.CUARTA, PosicionJuego.REVES,
                bloque(hoy.getDayOfWeek(), BloqueHorario.NOCHE), bloque(hoy.getDayOfWeek(), BloqueHorario.TARDE));
        MatchPareja match = MatchPareja.builder().id(9L).jugador1(yo).jugador2(sofia).build();
        Cancha cancha = Cancha.builder().id(3L).nombre("Central").activa(true).build();

        when(matchParejaRepository.findById(9L)).thenReturn(Optional.of(match));
        when(perfilJugadorRepository.findByUsuarioIdIn(anyCollection())).thenReturn(List.of(miPerfil, pSofia));
        when(canchaRepository.findByActivaTrue()).thenReturn(List.of(cancha));
        when(disponibilidadService.obtenerDisponibilidad(eq(3L), any(LocalDate.class))).thenAnswer(inv -> {
            LocalDate fecha = inv.getArgument(1);
            return new DisponibilidadResponse(3L, "Central", fecha, List.of(
                    slot(11L, LocalTime.of(15, 0), true),    // tarde: el usuario no puede
                    slot(12L, LocalTime.of(19, 30), true),   // noche libre: se sugiere
                    slot(13L, LocalTime.of(21, 0), false))); // noche ocupada
        });

        List<TurnoSugeridoResponse> turnos = matchService.sugerirTurnos(9L, "juan@test.com", 1);

        assertEquals(1, turnos.size());
        assertEquals(12L, turnos.get(0).franjaId());
        assertEquals(hoy, turnos.get(0).fecha());
        assertEquals(BloqueHorario.NOCHE, turnos.get(0).bloque());
        verify(disponibilidadService, times(1)).obtenerDisponibilidad(eq(3L), any(LocalDate.class));
    }

    private static DisponibilidadResponse.SlotDisponibilidad slot(Long franjaId, LocalTime inicio, boolean disponible) {
        return new DisponibilidadResponse.SlotDisponibilidad(franjaId, inicio, inicio.plusMinutes(90), 90,
                new BigDecimal("12000"), disponible, disponible ? null : "Reservado", List.of());
    }

    @Test
    void sugerirTurnos_deUnMatchAjeno_lanzaNotFound() {
        Usuario otro1 = usuario(7L, "Ana", "Lopez");
        Usuario otro2 = usuario(8L, "Beto", "Diaz");
        when(matchParejaRepository.findById(9L)).thenReturn(Optional.of(
                MatchPareja.builder().id(9L).jugador1(otro1).jugador2(otro2).build()));

        assertThrows(ResourceNotFoundException.class, () -> matchService.sugerirTurnos(9L, "juan@test.com", 7));
    }

    @Test
    void reservarJuntos_creaLaReservaAnombreDeQuienReservaYAvisaAlCompanero() {
        Usuario sofia = usuario(2L, "Sofia", "Ibarra");
        MatchPareja match = MatchPareja.builder().id(9L).jugador1(yo).jugador2(sofia).build();
        LocalDate fecha = LocalDate.now().plusDays(2);
        ReservaResponse reserva = new ReservaResponse(50L, 1L, "juan@test.com", 3L, "Central", 12L, fecha,
                LocalTime.of(19, 30), LocalTime.of(21, 0), new BigDecimal("12000"), EstadoReserva.PENDIENTE_PAGO,
                OrigenReserva.APP, null, LocalDateTime.now(), null);
        when(matchParejaRepository.findById(9L)).thenReturn(Optional.of(match));
        when(reservaService.crearReserva(any(ReservaRequest.class), eq("juan@test.com"))).thenReturn(reserva);

        ReservaResponse result = matchService.reservarJuntos(9L, new ReservaMatchRequest(3L, 12L, fecha, null), "juan@test.com");

        assertEquals(50L, result.id());
        ArgumentCaptor<ReservaRequest> request = ArgumentCaptor.forClass(ReservaRequest.class);
        verify(reservaService).crearReserva(request.capture(), eq("juan@test.com"));
        assertEquals(OrigenReserva.APP, request.getValue().origen());
        assertNull(request.getValue().usuarioId());
        verify(notificacionInAppService).crear(eq(sofia), eq(TipoNotificacion.MATCH_TURNO_RESERVADO), any(), any(), eq(50L));
    }

    @Test
    void afinidad_premiaPosicionesComplementariasYHorariosCompartidos() {
        Usuario otro = usuario(2L, "Ana", "Lopez");
        PerfilJugador mismaPosicion = perfil(otro, CategoriaJugador.CUARTA, PosicionJuego.DRIVE);
        PerfilJugador complementaria = perfil(otro, CategoriaJugador.CUARTA, PosicionJuego.REVES);
        PerfilJugador categoriaVecina = perfil(otro, CategoriaJugador.QUINTA, PosicionJuego.REVES);

        assertTrue(MatchServiceImpl.afinidad(miPerfil, complementaria, 2) > MatchServiceImpl.afinidad(miPerfil, mismaPosicion, 2));
        assertTrue(MatchServiceImpl.afinidad(miPerfil, complementaria, 2) > MatchServiceImpl.afinidad(miPerfil, categoriaVecina, 2));
        assertEquals(100, MatchServiceImpl.afinidad(miPerfil, complementaria, 10));
        Set<Integer> valores = List.of(1, 2, 3, 4, 5, 6).stream()
                .map(n -> MatchServiceImpl.afinidad(miPerfil, complementaria, n)).collect(Collectors.toSet());
        assertEquals(6, valores.size());
    }
}
