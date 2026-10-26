package com.padel.service.impl;

import com.padel.domain.entity.InscripcionTorneo;
import com.padel.domain.entity.PartidoTorneo;
import com.padel.domain.entity.RankingJugador;
import com.padel.domain.entity.Torneo;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoInscripcion;
import com.padel.domain.enums.EstadoPartido;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.FormatoTorneo;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.enums.TipoTorneo;
import com.padel.dto.request.InscripcionTorneoRequest;
import com.padel.dto.request.ResultadoPartidoRequest;
import com.padel.dto.request.TorneoRequest;
import com.padel.dto.response.InscripcionTorneoResponse;
import com.padel.dto.response.PartidoTorneoResponse;
import com.padel.dto.response.TorneoResponse;
import com.padel.exception.CupoCompletoException;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.InscripcionTorneoMapper;
import com.padel.mapper.PartidoTorneoMapper;
import com.padel.mapper.TorneoMapper;
import com.padel.repository.InscripcionTorneoRepository;
import com.padel.repository.PartidoTorneoRepository;
import com.padel.repository.RankingJugadorRepository;
import com.padel.repository.TorneoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.NotificacionInAppService;
import com.padel.service.TorneoService;
import com.padel.util.EmailUtil;
import com.padel.util.FechaHoraUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TorneoServiceImpl implements TorneoService {

    private static final int PUNTOS_VICTORIA = 3;
    private static final int PUNTOS_DERROTA = 1;

    private final TorneoRepository torneoRepository;
    private final InscripcionTorneoRepository inscripcionTorneoRepository;
    private final PartidoTorneoRepository partidoTorneoRepository;
    private final RankingJugadorRepository rankingJugadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final TorneoMapper torneoMapper;
    private final InscripcionTorneoMapper inscripcionTorneoMapper;
    private final PartidoTorneoMapper partidoTorneoMapper;
    private final FechaHoraUtil fechaHoraUtil;
    private final NotificacionInAppService notificacionInAppService;

    @Override
    public TorneoResponse crear(TorneoRequest request) {
        log.info("Creando nuevo torneo: {}", request.nombre());
        fechaHoraUtil.validarAnioRazonable(request.fechaInicio(), "la fecha de inicio");
        fechaHoraUtil.validarAnioRazonable(request.fechaFin(), "la fecha de fin");
        if (request.fechaFin().isBefore(request.fechaInicio())) {
            throw new DatosInvalidosException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        Torneo torneo = torneoMapper.toEntity(request);
        Torneo saved = torneoRepository.save(torneo);
        return torneoMapper.toResponse(saved, 0);
    }

    @Override
    public TorneoResponse actualizar(Long id, TorneoRequest request) {
        log.info("Actualizando torneo ID: {}", id);
        Torneo torneo = obtenerTorneoOrThrow(id);
        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("Sólo se puede editar un torneo mientras la inscripción está abierta");
        }
        fechaHoraUtil.validarAnioRazonable(request.fechaInicio(), "la fecha de inicio");
        fechaHoraUtil.validarAnioRazonable(request.fechaFin(), "la fecha de fin");
        if (request.fechaFin().isBefore(request.fechaInicio())) {
            throw new DatosInvalidosException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        torneoMapper.updateFromRequest(request, torneo);
        Torneo saved = torneoRepository.save(torneo);
        long inscriptas = inscripcionTorneoRepository.countByTorneoIdAndEstado(id, EstadoInscripcion.CONFIRMADA);
        return torneoMapper.toResponse(saved, inscriptas);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TorneoResponse> buscar(EstadoTorneo estado, CategoriaJugador categoria, TipoTorneo tipo) {
        List<Torneo> torneos = torneoRepository.buscar(estado, categoria, tipo);
        if (torneos.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> confirmadas = new HashMap<>();
        for (Object[] fila : inscripcionTorneoRepository.contarConfirmadasPorTorneo(torneos.stream().map(Torneo::getId).toList())) {
            confirmadas.put((Long) fila[0], ((Number) fila[1]).longValue());
        }
        return torneos.stream()
                .map(t -> torneoMapper.toResponse(t, confirmadas.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TorneoResponse obtenerPorId(Long id) {
        Torneo torneo = obtenerTorneoOrThrow(id);
        long inscriptas = inscripcionTorneoRepository.countByTorneoIdAndEstado(id, EstadoInscripcion.CONFIRMADA);
        return torneoMapper.toResponse(torneo, inscriptas);
    }

    @Override
    public void cancelar(Long id) {
        log.info("Cancelando torneo ID: {}", id);
        Torneo torneo = obtenerTorneoOrThrow(id);
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO || torneo.getEstado() == EstadoTorneo.CANCELADO) {
            throw new EstadoInvalidoException("No se puede cancelar un torneo en estado " + torneo.getEstado());
        }
        torneo.setEstado(EstadoTorneo.CANCELADO);
        torneoRepository.save(torneo);
    }

    @Override
    public TorneoResponse suspender(Long id, String motivo) {
        log.info("Suspendiendo torneo ID: {} (motivo: {})", id, motivo);
        Torneo torneo = obtenerTorneoOrThrow(id);
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO || torneo.getEstado() == EstadoTorneo.CANCELADO) {
            throw new EstadoInvalidoException("No se puede suspender un torneo en estado " + torneo.getEstado());
        }
        torneo.setEstado(EstadoTorneo.SUSPENDIDO);
        torneo.setMotivoSuspension(motivo != null && !motivo.isBlank() ? motivo : "Suspendido por el administrador");
        Torneo saved = torneoRepository.save(torneo);
        long inscriptas = inscripcionTorneoRepository.countByTorneoIdAndEstado(id, EstadoInscripcion.CONFIRMADA);
        return torneoMapper.toResponse(saved, inscriptas);
    }

    @Override
    public TorneoResponse reactivar(Long id) {
        log.info("Reactivando torneo ID: {}", id);
        Torneo torneo = obtenerTorneoOrThrow(id);
        if (torneo.getEstado() != EstadoTorneo.SUSPENDIDO) {
            throw new EstadoInvalidoException("Sólo se puede reactivar un torneo suspendido");
        }
        // Si aún no llegó la fecha de inicio, vuelve a inscripción; si ya empezó, a en curso.
        boolean yaEmpezo = torneo.getFechaInicio() != null && !torneo.getFechaInicio().isAfter(java.time.LocalDate.now());
        torneo.setEstado(yaEmpezo ? EstadoTorneo.EN_CURSO : EstadoTorneo.INSCRIPCION_ABIERTA);
        torneo.setMotivoSuspension(null);
        Torneo saved = torneoRepository.save(torneo);
        long inscriptas = inscripcionTorneoRepository.countByTorneoIdAndEstado(id, EstadoInscripcion.CONFIRMADA);
        return torneoMapper.toResponse(saved, inscriptas);
    }

    /**
     * Invita a un compañero: la pareja queda PENDIENTE_ACEPTACION hasta que él acepte. Antes la inscripción
     * quedaba confirmada sin su consentimiento y lo bloqueaba para anotarse con otra pareja.
     */
    @Override
    public InscripcionTorneoResponse inscribirPareja(Long torneoId, InscripcionTorneoRequest request, String usuarioEmail) {
        Torneo torneo = obtenerTorneoOrThrow(torneoId);
        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("El torneo no admite inscripciones en su estado actual");
        }
        Usuario jugador1 = buscarUsuario(usuarioEmail);
        Usuario jugador2 = usuarioRepository.findByEmail(EmailUtil.normalizar(request.companeroEmail()))
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new DatosInvalidosException("No encontramos un jugador habilitado con ese email. Pedile que se registre en PadelApp."));
        if (jugador1.getId().equals(jugador2.getId())) {
            throw new DatosInvalidosException("No podés inscribirte en pareja con vos mismo");
        }

        List<InscripcionTorneo> vigentes = inscripcionTorneoRepository.findByTorneoIdAndEstadoIn(torneoId,
                List.of(EstadoInscripcion.PENDIENTE_ACEPTACION, EstadoInscripcion.CONFIRMADA));
        if (vigentes.stream().anyMatch(i -> i.getJugador1().getId().equals(jugador1.getId())
                || (i.getEstado() == EstadoInscripcion.CONFIRMADA && i.incluyeA(jugador1.getId())))) {
            throw new DatosInvalidosException("Ya tenés una inscripción o una invitación activa en este torneo");
        }
        if (vigentes.stream().anyMatch(i -> i.getEstado() == EstadoInscripcion.CONFIRMADA && i.incluyeA(jugador2.getId()))) {
            throw new DatosInvalidosException("Tu compañero ya está inscripto en otra pareja de este torneo");
        }
        if (inscripcionTorneoRepository.countByTorneoIdAndEstado(torneoId, EstadoInscripcion.CONFIRMADA) >= torneo.getMaxParejas()) {
            throw new CupoCompletoException("El torneo ya alcanzó el cupo máximo de parejas");
        }

        InscripcionTorneo invitacion = inscripcionTorneoRepository.save(InscripcionTorneo.builder()
                .torneo(torneo)
                .jugador1(jugador1)
                .jugador2(jugador2)
                .estado(EstadoInscripcion.PENDIENTE_ACEPTACION)
                .build());
        notificacionInAppService.crear(jugador2, TipoNotificacion.INVITACION_TORNEO,
                "Te invitaron a un torneo",
                String.format("%s %s te invitó a jugar \"%s\" en pareja. Aceptá o rechazá la invitación desde el torneo.",
                        jugador1.getNombre(), jugador1.getApellido(), torneo.getNombre()),
                torneoId);
        log.info("Invitación ID {} al torneo ID {} enviada", invitacion.getId(), torneoId);
        return inscripcionTorneoMapper.toResponse(invitacion);
    }

    @Override
    public InscripcionTorneoResponse aceptarInvitacion(Long torneoId, Long inscripcionId, String usuarioEmail) {
        Torneo torneo = torneoRepository.findByIdForUpdate(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado con ID: " + torneoId));
        InscripcionTorneo invitacion = invitacionParaResponder(torneoId, inscripcionId, usuarioEmail);
        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("El torneo ya no admite inscripciones");
        }
        List<InscripcionTorneo> confirmadas = inscripcionTorneoRepository.findByTorneoIdAndEstado(torneoId, EstadoInscripcion.CONFIRMADA);
        if (confirmadas.size() >= torneo.getMaxParejas()) {
            throw new CupoCompletoException("El torneo ya alcanzó el cupo máximo de parejas");
        }
        Long id1 = invitacion.getJugador1().getId();
        Long id2 = invitacion.getJugador2().getId();
        if (confirmadas.stream().anyMatch(i -> i.incluyeA(id1) || i.incluyeA(id2))) {
            throw new DatosInvalidosException("Uno de los dos ya está inscripto en otra pareja de este torneo");
        }

        invitacion.setEstado(EstadoInscripcion.CONFIRMADA);
        inscripcionTorneoRepository.save(invitacion);
        // Las demás invitaciones pendientes de cualquiera de los dos en este torneo ya no tienen sentido.
        List<InscripcionTorneo> otrasPendientes = inscripcionTorneoRepository.findByTorneoIdAndEstado(torneoId, EstadoInscripcion.PENDIENTE_ACEPTACION)
                .stream()
                .filter(i -> !i.getId().equals(invitacion.getId()) && (i.incluyeA(id1) || i.incluyeA(id2)))
                .toList();
        otrasPendientes.forEach(i -> i.setEstado(EstadoInscripcion.CANCELADA));
        inscripcionTorneoRepository.saveAll(otrasPendientes);

        avisarRespuesta(invitacion, torneo, "aceptó");
        return inscripcionTorneoMapper.toResponse(invitacion);
    }

    @Override
    public InscripcionTorneoResponse rechazarInvitacion(Long torneoId, Long inscripcionId, String usuarioEmail) {
        Torneo torneo = obtenerTorneoOrThrow(torneoId);
        InscripcionTorneo invitacion = invitacionParaResponder(torneoId, inscripcionId, usuarioEmail);
        invitacion.setEstado(EstadoInscripcion.RECHAZADA);
        inscripcionTorneoRepository.save(invitacion);
        avisarRespuesta(invitacion, torneo, "rechazó");
        return inscripcionTorneoMapper.toResponse(invitacion);
    }

    private InscripcionTorneo invitacionParaResponder(Long torneoId, Long inscripcionId, String usuarioEmail) {
        Usuario usuario = buscarUsuario(usuarioEmail);
        InscripcionTorneo invitacion = inscripcionTorneoRepository.findByIdAndTorneoId(inscripcionId, torneoId)
                .filter(i -> i.getJugador2().getId().equals(usuario.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Invitación no encontrada"));
        if (invitacion.getEstado() != EstadoInscripcion.PENDIENTE_ACEPTACION) {
            throw new EstadoInvalidoException("La invitación ya fue respondida");
        }
        return invitacion;
    }

    private void avisarRespuesta(InscripcionTorneo invitacion, Torneo torneo, String verbo) {
        Usuario invitado = invitacion.getJugador2();
        notificacionInAppService.crear(invitacion.getJugador1(), TipoNotificacion.INVITACION_TORNEO_RESPONDIDA,
                "Respondieron tu invitación",
                String.format("%s %s %s tu invitación para jugar \"%s\".", invitado.getNombre(), invitado.getApellido(), verbo, torneo.getNombre()),
                torneo.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionTorneoResponse> listarInscripciones(Long torneoId, String usuarioEmail) {
        obtenerTorneoOrThrow(torneoId);
        Usuario usuario = buscarUsuario(usuarioEmail);
        boolean esAdmin = usuario.getRol() == RolUsuario.ADMIN;
        return inscripcionTorneoRepository.findByTorneoIdAndEstadoIn(torneoId,
                        List.of(EstadoInscripcion.CONFIRMADA, EstadoInscripcion.PENDIENTE_ACEPTACION)).stream()
                .filter(i -> i.getEstado() == EstadoInscripcion.CONFIRMADA || esAdmin || i.incluyeA(usuario.getId()))
                .map(inscripcionTorneoMapper::toResponse)
                .toList();
    }

    @Override
    public void cancelarInscripcion(Long torneoId, Long inscripcionId, String usuarioEmail) {
        log.info("Cancelando la inscripción ID {} del torneo ID {}", inscripcionId, torneoId);
        Torneo torneo = obtenerTorneoOrThrow(torneoId);
        InscripcionTorneo inscripcion = inscripcionTorneoRepository.findByIdAndTorneoId(inscripcionId, torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripción no encontrada con ID: " + inscripcionId));

        Usuario usuario = buscarUsuario(usuarioEmail);

        boolean esJugadorDeLaPareja = inscripcion.incluyeA(usuario.getId());
        boolean esAdmin = usuario.getRol() == RolUsuario.ADMIN;
        if (!esJugadorDeLaPareja && !esAdmin) {
            throw new AccessDeniedException("No tiene permisos para cancelar esta inscripción");
        }

        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("No se puede cancelar la inscripción una vez iniciado el torneo");
        }
        if (!inscripcion.getEstado().vigente()) {
            throw new EstadoInvalidoException("La inscripción ya no está activa");
        }

        inscripcion.setEstado(EstadoInscripcion.CANCELADA);
        inscripcionTorneoRepository.save(inscripcion);
    }

    @Override
    public List<PartidoTorneoResponse> generarFixture(Long torneoId) {
        log.info("Generando fixture para torneo ID {}", torneoId);
        Torneo torneo = obtenerTorneoOrThrow(torneoId);

        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("El torneo no está en estado de inscripción abierta");
        }
        if (partidoTorneoRepository.existsByTorneoId(torneoId)) {
            throw new EstadoInvalidoException("El fixture de este torneo ya fue generado");
        }

        List<InscripcionTorneo> inscripciones = new ArrayList<>(
                inscripcionTorneoRepository.findByTorneoIdAndEstado(torneoId, EstadoInscripcion.CONFIRMADA));
        if (inscripciones.size() < 2) {
            throw new DatosInvalidosException("Se necesitan al menos 2 parejas inscriptas para generar el fixture");
        }

        List<PartidoTorneo> partidos = torneo.getFormato() == FormatoTorneo.LIGA_TODOS_CONTRA_TODOS
                ? generarFixtureLiga(torneo, inscripciones)
                : generarPrimeraRondaEliminacion(torneo, inscripciones);

        partidoTorneoRepository.saveAll(partidos);
        // Al cerrar la inscripción, las invitaciones que nadie respondió quedan sin efecto.
        List<InscripcionTorneo> sinResponder = inscripcionTorneoRepository.findByTorneoIdAndEstado(torneoId, EstadoInscripcion.PENDIENTE_ACEPTACION);
        sinResponder.forEach(i -> i.setEstado(EstadoInscripcion.CANCELADA));
        inscripcionTorneoRepository.saveAll(sinResponder);
        torneo.setEstado(EstadoTorneo.EN_CURSO);
        torneoRepository.save(torneo);

        return partidos.stream().map(partidoTorneoMapper::toResponse).toList();
    }

    private List<PartidoTorneo> generarFixtureLiga(Torneo torneo, List<InscripcionTorneo> inscripciones) {
        List<PartidoTorneo> partidos = new ArrayList<>();
        for (int i = 0; i < inscripciones.size(); i++) {
            for (int j = i + 1; j < inscripciones.size(); j++) {
                partidos.add(PartidoTorneo.builder()
                        .torneo(torneo)
                        .ronda("Liga")
                        .numeroRonda(1)
                        .inscripcion1(inscripciones.get(i))
                        .inscripcion2(inscripciones.get(j))
                        .estado(EstadoPartido.PENDIENTE)
                        .build());
            }
        }
        return partidos;
    }

    private List<PartidoTorneo> generarPrimeraRondaEliminacion(Torneo torneo, List<InscripcionTorneo> inscripciones) {
        Collections.shuffle(inscripciones);
        String nombreRonda = nombreRondaEliminacion(inscripciones.size());
        List<PartidoTorneo> partidos = new ArrayList<>();

        int i = 0;
        while (i < inscripciones.size()) {
            InscripcionTorneo pareja1 = inscripciones.get(i);
            InscripcionTorneo pareja2 = (i + 1 < inscripciones.size()) ? inscripciones.get(i + 1) : null;

            PartidoTorneo.PartidoTorneoBuilder builder = PartidoTorneo.builder()
                    .torneo(torneo)
                    .ronda(nombreRonda)
                    .numeroRonda(1)
                    .inscripcion1(pareja1)
                    .inscripcion2(pareja2);

            if (pareja2 == null) {
                // Bye: la pareja avanza automáticamente sin sumar puntos de ranking
                builder.estado(EstadoPartido.FINALIZADO).ganador(pareja1);
            } else {
                builder.estado(EstadoPartido.PENDIENTE);
            }
            partidos.add(builder.build());
            i += 2;
        }
        return partidos;
    }

    private String nombreRondaEliminacion(int cantidadParejas) {
        if (cantidadParejas <= 2) return "Final";
        if (cantidadParejas <= 4) return "Semifinal";
        if (cantidadParejas <= 8) return "Cuartos de Final";
        if (cantidadParejas <= 16) return "Octavos de Final";
        return "Ronda de " + cantidadParejas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartidoTorneoResponse> listarPartidos(Long torneoId) {
        obtenerTorneoOrThrow(torneoId);
        return partidoTorneoRepository.findByTorneoIdOrderByNumeroRondaAscIdAsc(torneoId).stream()
                .map(partidoTorneoMapper::toResponse)
                .toList();
    }

    @Override
    public PartidoTorneoResponse cargarResultado(Long torneoId, Long partidoId, ResultadoPartidoRequest request) {
        log.info("Cargando resultado del partido ID {} del torneo ID {}", partidoId, torneoId);
        Torneo torneo = obtenerTorneoOrThrow(torneoId);
        validarEnCurso(torneo);
        PartidoTorneo partido = partidoTorneoRepository.findByIdAndTorneoId(partidoId, torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + partidoId));

        if (partido.getEstado() == EstadoPartido.FINALIZADO) {
            throw new EstadoInvalidoException("El resultado de este partido ya fue cargado");
        }
        if (partido.getInscripcion2() == null) {
            throw new EstadoInvalidoException("Este partido es un bye y no admite carga de resultado");
        }
        if (request.setsPareja1().equals(request.setsPareja2())) {
            throw new DatosInvalidosException("El resultado no puede terminar en empate");
        }

        InscripcionTorneo ganador = request.setsPareja1() > request.setsPareja2()
                ? partido.getInscripcion1()
                : partido.getInscripcion2();
        InscripcionTorneo perdedor = ganador.getId().equals(partido.getInscripcion1().getId())
                ? partido.getInscripcion2()
                : partido.getInscripcion1();

        partido.setSetsPareja1(request.setsPareja1());
        partido.setSetsPareja2(request.setsPareja2());
        partido.setGanador(ganador);
        partido.setEstado(EstadoPartido.FINALIZADO);
        PartidoTorneo saved = partidoTorneoRepository.save(partido);

        CategoriaJugador categoria = partido.getTorneo().getCategoria();
        actualizarRanking(ganador.getJugador1(), categoria, true);
        actualizarRanking(ganador.getJugador2(), categoria, true);
        actualizarRanking(perdedor.getJugador1(), categoria, false);
        actualizarRanking(perdedor.getJugador2(), categoria, false);

        // Una liga no avanza por rondas ("siguiente ronda" es solo para eliminación directa): sin esto
        // quedaba EN_CURSO para siempre. Termina cuando se jugaron todos sus partidos.
        if (torneo.getFormato() == FormatoTorneo.LIGA_TODOS_CONTRA_TODOS) {
            boolean quedanPendientes = partidoTorneoRepository.findByTorneoIdOrderByNumeroRondaAscIdAsc(torneoId).stream()
                    .anyMatch(p -> p.getEstado() != EstadoPartido.FINALIZADO);
            if (!quedanPendientes) {
                torneo.setEstado(EstadoTorneo.FINALIZADO);
                torneoRepository.save(torneo);
                log.info("Liga ID {} finalizada: se cargaron todos sus resultados", torneoId);
            }
        }

        return partidoTorneoMapper.toResponse(saved);
    }

    // Cargar resultados y avanzar rondas solo tiene sentido con el torneo en juego: no si está
    // suspendido, cancelado o ya finalizado.
    private void validarEnCurso(Torneo torneo) {
        if (torneo.getEstado() != EstadoTorneo.EN_CURSO) {
            throw new EstadoInvalidoException("El torneo no está en curso (estado actual: " + torneo.getEstado() + ")");
        }
    }

    private void actualizarRanking(Usuario jugador, CategoriaJugador categoria, boolean gano) {
        RankingJugador ranking = rankingJugadorRepository.findByJugadorIdAndCategoria(jugador.getId(), categoria)
                .orElseGet(() -> RankingJugador.builder()
                        .jugador(jugador)
                        .categoria(categoria)
                        .puntos(0)
                        .partidosJugados(0)
                        .partidosGanados(0)
                        .build());

        ranking.setPartidosJugados(ranking.getPartidosJugados() + 1);
        if (gano) {
            ranking.setPartidosGanados(ranking.getPartidosGanados() + 1);
            ranking.setPuntos(ranking.getPuntos() + PUNTOS_VICTORIA);
        } else {
            ranking.setPuntos(ranking.getPuntos() + PUNTOS_DERROTA);
        }
        rankingJugadorRepository.save(ranking);
    }

    @Override
    public List<PartidoTorneoResponse> generarSiguienteRonda(Long torneoId) {
        log.info("Generando siguiente ronda para torneo ID {}", torneoId);
        Torneo torneo = obtenerTorneoOrThrow(torneoId);

        if (torneo.getFormato() != FormatoTorneo.ELIMINACION_DIRECTA) {
            throw new DatosInvalidosException("Sólo los torneos de eliminación directa avanzan por rondas");
        }
        validarEnCurso(torneo);

        Integer ultimaRonda = partidoTorneoRepository.obtenerUltimaRonda(torneoId);
        if (ultimaRonda == null) {
            throw new EstadoInvalidoException("Primero hay que generar el fixture del torneo");
        }

        List<PartidoTorneo> partidosRondaActual = partidoTorneoRepository.findByTorneoIdAndNumeroRonda(torneoId, ultimaRonda);
        boolean todosFinalizados = partidosRondaActual.stream().allMatch(p -> p.getEstado() == EstadoPartido.FINALIZADO);
        if (!todosFinalizados) {
            throw new EstadoInvalidoException("Aún hay partidos pendientes en la ronda actual");
        }

        if (partidosRondaActual.size() == 1) {
            torneo.setEstado(EstadoTorneo.FINALIZADO);
            torneoRepository.save(torneo);
            return List.of();
        }

        List<InscripcionTorneo> ganadores = partidosRondaActual.stream()
                .map(PartidoTorneo::getGanador)
                .toList();

        int numeroRonda = ultimaRonda + 1;
        String nombreRonda = nombreRondaEliminacion(ganadores.size());
        List<PartidoTorneo> nuevaRonda = new ArrayList<>();
        for (int i = 0; i < ganadores.size(); i += 2) {
            InscripcionTorneo pareja1 = ganadores.get(i);
            InscripcionTorneo pareja2 = (i + 1 < ganadores.size()) ? ganadores.get(i + 1) : null;

            PartidoTorneo.PartidoTorneoBuilder builder = PartidoTorneo.builder()
                    .torneo(torneo)
                    .ronda(nombreRonda)
                    .numeroRonda(numeroRonda)
                    .inscripcion1(pareja1)
                    .inscripcion2(pareja2);

            if (pareja2 == null) {
                builder.estado(EstadoPartido.FINALIZADO).ganador(pareja1);
            } else {
                builder.estado(EstadoPartido.PENDIENTE);
            }
            nuevaRonda.add(builder.build());
        }

        partidoTorneoRepository.saveAll(nuevaRonda);
        return nuevaRonda.stream().map(partidoTorneoMapper::toResponse).toList();
    }

    private Torneo obtenerTorneoOrThrow(Long id) {
        return torneoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado con ID: " + id));
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
