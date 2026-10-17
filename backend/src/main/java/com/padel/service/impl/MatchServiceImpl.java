package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.DisponibilidadJugador;
import com.padel.domain.entity.MatchPareja;
import com.padel.domain.entity.MatchSwipe;
import com.padel.domain.entity.PerfilJugador;
import com.padel.domain.entity.RankingJugador;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.BloqueHorario;
import com.padel.domain.enums.OrigenReserva;
import com.padel.domain.enums.PosicionJuego;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.enums.TipoSwipe;
import com.padel.dto.request.ReservaMatchRequest;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.request.SwipeRequest;
import com.padel.dto.response.CandidatoMatchResponse;
import com.padel.dto.response.DisponibilidadResponse;
import com.padel.dto.response.MatchParejaResponse;
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
import com.padel.service.MatchService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.ReservaService;
import com.padel.util.FechaHoraUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MatchServiceImpl implements MatchService {

    static final int LIMITE_MAXIMO_CANDIDATOS = 50;
    static final int DIAS_MAXIMOS_SUGERENCIA = 14;
    static final int TURNOS_MAXIMOS_SUGERIDOS = 30;
    // Con 6 horarios en común la disponibilidad ya aporta su peso completo a la afinidad.
    private static final int HORARIOS_PARA_AFINIDAD_PLENA = 6;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM");

    private final PerfilJugadorRepository perfilJugadorRepository;
    private final MatchSwipeRepository matchSwipeRepository;
    private final MatchParejaRepository matchParejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RankingJugadorRepository rankingJugadorRepository;
    private final CanchaRepository canchaRepository;
    private final DisponibilidadService disponibilidadService;
    private final ReservaService reservaService;
    private final NotificacionInAppService notificacionInAppService;
    private final MatchMapper matchMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CandidatoMatchResponse> obtenerCandidatos(String email, int limite) {
        Usuario yo = obtenerUsuario(email);
        PerfilJugador miPerfil = obtenerPerfilParaMatch(yo);
        Set<Long> yaRespondidos = matchSwipeRepository.findDestinoIdsByOrigenId(yo.getId());

        // Compatibles: categoría a lo sumo una de distancia y al menos un horario en común.
        List<PerfilJugador> compatibles = perfilJugadorRepository.findByVisibleEnMatchTrueAndUsuarioActivoTrue().stream()
                .filter(p -> !p.getUsuario().getId().equals(yo.getId()))
                .filter(p -> !yaRespondidos.contains(p.getUsuario().getId()))
                .filter(p -> distanciaCategoria(miPerfil, p) <= 1)
                .filter(p -> !coincidencias(miPerfil, p).isEmpty())
                .toList();
        if (compatibles.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RankingJugador>> rankings = rankingJugadorRepository
                .findByJugadorIdIn(compatibles.stream().map(p -> p.getUsuario().getId()).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getJugador().getId()));

        return compatibles.stream()
                .map(p -> {
                    Set<DisponibilidadJugador> enComun = coincidencias(miPerfil, p);
                    return matchMapper.toCandidato(p, puntosRanking(rankings, p), afinidad(miPerfil, p, enComun.size()), enComun);
                })
                .sorted(Comparator.comparingInt(CandidatoMatchResponse::afinidad).reversed()
                        .thenComparing(CandidatoMatchResponse::usuarioId))
                .limit(Math.max(1, Math.min(limite, LIMITE_MAXIMO_CANDIDATOS)))
                .toList();
    }

    @Override
    public SwipeResponse registrarSwipe(SwipeRequest request, String email) {
        Usuario yo = obtenerUsuario(email);
        if (request.usuarioId().equals(yo.getId())) {
            throw new DatosInvalidosException("No podés responderte a vos mismo");
        }
        PerfilJugador miPerfil = obtenerPerfilParaMatch(yo);
        PerfilJugador otro = perfilJugadorRepository.findByUsuarioId(request.usuarioId())
                .filter(PerfilJugador::isVisibleEnMatch)
                .filter(p -> p.getUsuario().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException("El jugador no está disponible en el match"));
        if (matchSwipeRepository.existsByOrigenIdAndDestinoId(yo.getId(), request.usuarioId())) {
            throw new EstadoInvalidoException("Ya respondiste a este jugador");
        }

        matchSwipeRepository.save(MatchSwipe.builder()
                .origen(yo)
                .destino(otro.getUsuario())
                .tipo(request.tipo())
                .build());

        boolean esMutuo = request.tipo() == TipoSwipe.LIKE
                && matchSwipeRepository.existsByOrigenIdAndDestinoIdAndTipo(request.usuarioId(), yo.getId(), TipoSwipe.LIKE);
        if (!esMutuo) {
            return new SwipeResponse(false, null);
        }

        MatchPareja match = crearMatchSiNoExiste(yo, otro.getUsuario());
        return new SwipeResponse(true,
                matchMapper.toPareja(match, otro.getUsuario(), otro, coincidencias(miPerfil, otro)));
    }

    @Override
    public List<MatchParejaResponse> obtenerMisParejas(String email) {
        Usuario yo = obtenerUsuario(email);
        repararMatchesPendientes(yo);

        List<MatchPareja> matches = matchParejaRepository.findDelUsuario(yo.getId());
        if (matches.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = matches.stream().map(m -> m.companeroDe(yo.getId()).getId()).collect(Collectors.toSet());
        ids.add(yo.getId());
        Map<Long, PerfilJugador> perfiles = perfilJugadorRepository.findByUsuarioIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getUsuario().getId(), Function.identity()));
        PerfilJugador miPerfil = perfiles.get(yo.getId());

        return matches.stream()
                .map(m -> {
                    Usuario companero = m.companeroDe(yo.getId());
                    PerfilJugador perfilCompanero = perfiles.get(companero.getId());
                    return matchMapper.toPareja(m, companero, perfilCompanero, coincidencias(miPerfil, perfilCompanero));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoSugeridoResponse> sugerirTurnos(Long matchId, String email, int dias) {
        Usuario yo = obtenerUsuario(email);
        MatchPareja match = obtenerMatchPropio(matchId, yo);
        Usuario companero = match.companeroDe(yo.getId());

        Map<Long, PerfilJugador> perfiles = perfilJugadorRepository.findByUsuarioIdIn(List.of(yo.getId(), companero.getId())).stream()
                .collect(Collectors.toMap(p -> p.getUsuario().getId(), Function.identity()));
        Set<DisponibilidadJugador> enComun = coincidencias(perfiles.get(yo.getId()), perfiles.get(companero.getId()));
        if (enComun.isEmpty()) {
            return List.of();
        }

        LocalDate hoy = LocalDate.now();
        LocalDate ultimoDia = Stream.of(hoy.plusDays(Math.max(1, Math.min(dias, DIAS_MAXIMOS_SUGERENCIA)) - 1L),
                        FechaHoraUtil.fechaMaximaReservable(hoy))
                .min(Comparator.naturalOrder())
                .orElse(hoy);
        List<Cancha> canchas = canchaRepository.findByActivaTrue();

        List<TurnoSugeridoResponse> sugeridos = new ArrayList<>();
        for (LocalDate fecha = hoy; !fecha.isAfter(ultimoDia) && sugeridos.size() < TURNOS_MAXIMOS_SUGERIDOS; fecha = fecha.plusDays(1)) {
            LocalDate dia = fecha;
            Set<BloqueHorario> bloquesDelDia = enComun.stream()
                    .filter(b -> b.getDiaSemana() == dia.getDayOfWeek())
                    .map(DisponibilidadJugador::getBloque)
                    .collect(Collectors.toSet());
            if (bloquesDelDia.isEmpty()) {
                continue;
            }
            for (Cancha cancha : canchas) {
                DisponibilidadResponse disponibilidad = disponibilidadService.obtenerDisponibilidad(cancha.getId(), dia);
                disponibilidad.slots().stream()
                        .filter(DisponibilidadResponse.SlotDisponibilidad::disponible)
                        .filter(s -> bloquesDelDia.contains(BloqueHorario.de(s.horaInicio())))
                        .forEach(s -> sugeridos.add(new TurnoSugeridoResponse(
                                dia, cancha.getId(), cancha.getNombre(), s.franjaId(),
                                s.horaInicio(), s.horaFin(), s.duracionMin(), s.precio(), BloqueHorario.de(s.horaInicio()))));
            }
        }

        return sugeridos.stream()
                .sorted(Comparator.comparing(TurnoSugeridoResponse::fecha)
                        .thenComparing(TurnoSugeridoResponse::horaInicio)
                        .thenComparing(TurnoSugeridoResponse::canchaNombre))
                .limit(TURNOS_MAXIMOS_SUGERIDOS)
                .toList();
    }

    @Override
    public ReservaResponse reservarJuntos(Long matchId, ReservaMatchRequest request, String email) {
        Usuario yo = obtenerUsuario(email);
        MatchPareja match = obtenerMatchPropio(matchId, yo);

        // La reserva queda a nombre de quien la hace (paga la seña); al compañero se le avisa.
        ReservaResponse reserva = reservaService.crearReserva(new ReservaRequest(
                request.canchaId(), request.franjaId(), request.fecha(), OrigenReserva.APP, request.duracionMin(), null), email);

        Usuario companero = match.companeroDe(yo.getId());
        notificacionInAppService.crear(
                companero,
                TipoNotificacion.MATCH_TURNO_RESERVADO,
                "Turno reservado con tu pareja",
                String.format("%s %s reservó %s el %s de %s a %s para jugar con vos.",
                        yo.getNombre(), yo.getApellido(), reserva.canchaNombre(),
                        reserva.fecha().format(FORMATO_FECHA),
                        reserva.horaInicio().toString(), reserva.horaFin().toString()),
                reserva.id()
        );
        log.info("Reserva ID {} creada desde el match ID {} por {}", reserva.id(), matchId, email);
        return reserva;
    }

    // Afinidad 0-100: mitad disponibilidad compartida, 30% cercanía de categoría, 20% posiciones.
    static int afinidad(PerfilJugador yo, PerfilJugador otro, int horariosEnComun) {
        double disponibilidad = Math.min(horariosEnComun, HORARIOS_PARA_AFINIDAD_PLENA) / (double) HORARIOS_PARA_AFINIDAD_PLENA;
        double categoria = distanciaCategoria(yo, otro) == 0 ? 1.0 : 0.6;
        double posicion = factorPosicion(yo.getPosicion(), otro.getPosicion());
        return (int) Math.round(100 * (0.5 * disponibilidad + 0.3 * categoria + 0.2 * posicion));
    }

    // Drive con revés es la pareja natural; quien juega "indistinto" se acomoda a cualquiera.
    private static double factorPosicion(PosicionJuego a, PosicionJuego b) {
        if (a == PosicionJuego.INDISTINTO || b == PosicionJuego.INDISTINTO) {
            return 0.8;
        }
        return a != b ? 1.0 : 0.4;
    }

    private static int distanciaCategoria(PerfilJugador a, PerfilJugador b) {
        return Math.abs(a.getCategoria().ordinal() - b.getCategoria().ordinal());
    }

    private static Set<DisponibilidadJugador> coincidencias(PerfilJugador a, PerfilJugador b) {
        if (a == null || b == null) {
            return Set.of();
        }
        Set<DisponibilidadJugador> enComun = new HashSet<>(a.getDisponibilidad());
        enComun.retainAll(b.getDisponibilidad());
        return enComun;
    }

    private static int puntosRanking(Map<Long, List<RankingJugador>> rankings, PerfilJugador perfil) {
        return rankings.getOrDefault(perfil.getUsuario().getId(), List.of()).stream()
                .filter(r -> r.getCategoria() == perfil.getCategoria())
                .mapToInt(RankingJugador::getPuntos)
                .findFirst()
                .orElse(0);
    }

    // Si dos jugadores se dieron "me interesa" en el mismo instante, ninguna de las dos transacciones
    // ve el like de la otra y el match no se crea: se completa acá la próxima vez que se listan.
    private void repararMatchesPendientes(Usuario yo) {
        for (Long otroId : matchSwipeRepository.findLikesMutuos(yo.getId())) {
            usuarioRepository.findById(otroId).ifPresent(otro -> crearMatchSiNoExiste(yo, otro));
        }
    }

    private MatchPareja crearMatchSiNoExiste(Usuario a, Usuario b) {
        Usuario jugador1 = a.getId() < b.getId() ? a : b;
        Usuario jugador2 = jugador1 == a ? b : a;
        return matchParejaRepository.findByJugador1IdAndJugador2Id(jugador1.getId(), jugador2.getId())
                .orElseGet(() -> {
                    MatchPareja nuevo = matchParejaRepository.save(MatchPareja.builder()
                            .jugador1(jugador1)
                            .jugador2(jugador2)
                            .build());
                    avisarMatch(jugador1, jugador2, nuevo);
                    avisarMatch(jugador2, jugador1, nuevo);
                    log.info("Nuevo match ID {} entre {} y {}", nuevo.getId(), jugador1.getEmail(), jugador2.getEmail());
                    return nuevo;
                });
    }

    private void avisarMatch(Usuario destinatario, Usuario companero, MatchPareja match) {
        notificacionInAppService.crear(
                destinatario,
                TipoNotificacion.MATCH_NUEVO,
                "¡Tenés un nuevo match!",
                String.format("Vos y %s %s quieren jugar juntos. Entrá a Buscar pareja para ver sus horarios en común y reservar.",
                        companero.getNombre(), companero.getApellido()),
                match.getId()
        );
    }

    private PerfilJugador obtenerPerfilParaMatch(Usuario usuario) {
        PerfilJugador perfil = perfilJugadorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new EstadoInvalidoException("Completá tu perfil de jugador para usar el match"));
        if (!perfil.isVisibleEnMatch()) {
            throw new EstadoInvalidoException("Tu perfil está oculto: activá la visibilidad para usar el match");
        }
        return perfil;
    }

    private MatchPareja obtenerMatchPropio(Long matchId, Usuario usuario) {
        return matchParejaRepository.findById(matchId)
                .filter(m -> m.incluye(usuario.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Match no encontrado"));
    }

    private Usuario obtenerUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }
}
