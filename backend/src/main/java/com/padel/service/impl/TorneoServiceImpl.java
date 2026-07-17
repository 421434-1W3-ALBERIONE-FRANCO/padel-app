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
import com.padel.service.TorneoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public TorneoResponse crear(TorneoRequest request) {
        log.info("Creando nuevo torneo: {}", request.nombre());
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
        return torneoRepository.buscar(estado, categoria, tipo).stream()
                .map(t -> torneoMapper.toResponse(t, inscripcionTorneoRepository.countByTorneoIdAndEstado(t.getId(), EstadoInscripcion.CONFIRMADA)))
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
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            throw new EstadoInvalidoException("No se puede cancelar un torneo ya finalizado");
        }
        torneo.setEstado(EstadoTorneo.CANCELADO);
        torneoRepository.save(torneo);
    }

    @Override
    public InscripcionTorneoResponse inscribirPareja(Long torneoId, InscripcionTorneoRequest request, String usuarioEmail) {
        log.info("Inscribiendo pareja en torneo ID {} por usuario {}", torneoId, usuarioEmail);
        Torneo torneo = obtenerTorneoOrThrow(torneoId);

        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("El torneo no admite inscripciones en su estado actual");
        }

        Usuario jugador1 = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        if (request.companeroEmail().equalsIgnoreCase(usuarioEmail)) {
            throw new DatosInvalidosException("No podés inscribirte en pareja con vos mismo");
        }

        Usuario jugador2 = usuarioRepository.findByEmail(request.companeroEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un jugador con email: " + request.companeroEmail()));

        long cupoActual = inscripcionTorneoRepository.countByTorneoIdAndEstado(torneoId, EstadoInscripcion.CONFIRMADA);
        if (cupoActual >= torneo.getMaxParejas()) {
            throw new CupoCompletoException("El torneo ya alcanzó el cupo máximo de parejas");
        }

        boolean yaInscriptos = inscripcionTorneoRepository.findByTorneoIdAndEstado(torneoId, EstadoInscripcion.CONFIRMADA).stream()
                .anyMatch(i -> contieneJugadores(i, jugador1.getId(), jugador2.getId()));
        if (yaInscriptos) {
            throw new DatosInvalidosException("Esta pareja ya está inscripta en el torneo");
        }

        InscripcionTorneo inscripcion = InscripcionTorneo.builder()
                .torneo(torneo)
                .jugador1(jugador1)
                .jugador2(jugador2)
                .estado(EstadoInscripcion.CONFIRMADA)
                .build();

        InscripcionTorneo saved = inscripcionTorneoRepository.save(inscripcion);
        return inscripcionTorneoMapper.toResponse(saved);
    }

    private boolean contieneJugadores(InscripcionTorneo inscripcion, Long jugador1Id, Long jugador2Id) {
        Long a = inscripcion.getJugador1().getId();
        Long b = inscripcion.getJugador2().getId();
        return (a.equals(jugador1Id) && b.equals(jugador2Id)) || (a.equals(jugador2Id) && b.equals(jugador1Id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionTorneoResponse> listarInscripciones(Long torneoId) {
        obtenerTorneoOrThrow(torneoId);
        return inscripcionTorneoRepository.findByTorneoId(torneoId).stream()
                .map(inscripcionTorneoMapper::toResponse)
                .toList();
    }

    @Override
    public void cancelarInscripcion(Long torneoId, Long inscripcionId, String usuarioEmail) {
        log.info("Cancelando inscripción ID {} del torneo ID {} por usuario {}", inscripcionId, torneoId, usuarioEmail);
        Torneo torneo = obtenerTorneoOrThrow(torneoId);
        InscripcionTorneo inscripcion = inscripcionTorneoRepository.findByIdAndTorneoId(inscripcionId, torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripción no encontrada con ID: " + inscripcionId));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        boolean esJugadorDeLaPareja = inscripcion.getJugador1().getId().equals(usuario.getId())
                || inscripcion.getJugador2().getId().equals(usuario.getId());
        boolean esAdmin = usuario.getRol() == com.padel.domain.enums.RolUsuario.ADMIN;
        if (!esJugadorDeLaPareja && !esAdmin) {
            throw new AccessDeniedException("No tiene permisos para cancelar esta inscripción");
        }

        if (torneo.getEstado() != EstadoTorneo.INSCRIPCION_ABIERTA) {
            throw new EstadoInvalidoException("No se puede cancelar la inscripción una vez iniciado el torneo");
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
        obtenerTorneoOrThrow(torneoId);
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

        return partidoTorneoMapper.toResponse(saved);
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
}
