package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.FranjaHoraria;
import com.padel.domain.entity.PostulacionSolicitud;
import com.padel.domain.entity.SolicitudPartido;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoPostulacion;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;
import com.padel.dto.request.PostulacionSolicitudRequest;
import com.padel.dto.request.SolicitudPartidoRequest;
import com.padel.dto.response.PostulacionSolicitudResponse;
import com.padel.dto.response.SolicitudPartidoResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.PostulacionSolicitudMapper;
import com.padel.mapper.SolicitudPartidoMapper;
import com.padel.repository.CanchaRepository;
import com.padel.repository.FranjaHorariaRepository;
import com.padel.repository.PostulacionSolicitudRepository;
import com.padel.repository.SolicitudPartidoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.SolicitudPartidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SolicitudPartidoServiceImpl implements SolicitudPartidoService {

    private final SolicitudPartidoRepository solicitudPartidoRepository;
    private final PostulacionSolicitudRepository postulacionSolicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final CanchaRepository canchaRepository;
    private final FranjaHorariaRepository franjaHorariaRepository;
    private final SolicitudPartidoMapper solicitudPartidoMapper;
    private final PostulacionSolicitudMapper postulacionSolicitudMapper;

    @Override
    public SolicitudPartidoResponse crear(SolicitudPartidoRequest request, String creadorEmail) {
        log.info("Creando solicitud de partido para {} en categoría {}", creadorEmail, request.categoria());
        Usuario creador = usuarioRepository.findByEmail(creadorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + creadorEmail));

        Cancha cancha = null;
        if (request.canchaId() != null) {
            cancha = canchaRepository.findById(request.canchaId())
                    .filter(Cancha::isActiva)
                    .orElseThrow(() -> new ResourceNotFoundException("Cancha no encontrada o inactiva"));
            validarHorarioHabilitado(cancha, request.fechaHoraPropuesta());
        }

        SolicitudPartido solicitud = SolicitudPartido.builder()
                .creador(creador)
                .tipoSolicitud(request.tipoSolicitud())
                .categoria(request.categoria())
                .cantidadJugadoresFaltantes(request.cantidadJugadoresFaltantes())
                .fechaHoraPropuesta(request.fechaHoraPropuesta())
                .cancha(cancha)
                .descripcion(request.descripcion())
                .estado(EstadoSolicitud.ABIERTA)
                .build();

        SolicitudPartido saved = solicitudPartidoRepository.save(solicitud);
        return armarResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPartidoResponse> buscar(TipoSolicitud tipoSolicitud, CategoriaJugador categoria, EstadoSolicitud estado) {
        EstadoSolicitud estadoFiltro = estado != null ? estado : EstadoSolicitud.ABIERTA;
        return solicitudPartidoRepository.buscar(estadoFiltro, categoria, tipoSolicitud).stream()
                .map(this::armarResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitudPartidoResponse obtenerPorId(Long id) {
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(id);
        return armarResponse(solicitud);
    }

    @Override
    public void cancelar(Long id, String usuarioEmail) {
        log.info("Cancelando solicitud ID {} por usuario {}", id, usuarioEmail);
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(id);
        validarEsCreador(solicitud, usuarioEmail);

        if (solicitud.getEstado() == EstadoSolicitud.CANCELADA) {
            throw new EstadoInvalidoException("La solicitud ya está cancelada");
        }
        solicitud.setEstado(EstadoSolicitud.CANCELADA);
        solicitudPartidoRepository.save(solicitud);
    }

    @Override
    public SolicitudPartidoResponse postularse(Long solicitudId, PostulacionSolicitudRequest request, String jugadorEmail) {
        log.info("Usuario {} postulándose a solicitud ID {}", jugadorEmail, solicitudId);
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(solicitudId);

        if (solicitud.getEstado() != EstadoSolicitud.ABIERTA) {
            throw new EstadoInvalidoException("La solicitud ya no admite postulaciones");
        }

        Usuario jugador = usuarioRepository.findByEmail(jugadorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + jugadorEmail));

        if (solicitud.getCreador().getId().equals(jugador.getId())) {
            throw new DatosInvalidosException("No podés postularte a tu propia solicitud");
        }

        if (postulacionSolicitudRepository.existsBySolicitudIdAndJugadorId(solicitudId, jugador.getId())) {
            throw new DatosInvalidosException("Ya te postulaste a esta solicitud");
        }

        PostulacionSolicitud postulacion = PostulacionSolicitud.builder()
                .solicitud(solicitud)
                .jugador(jugador)
                .mensaje(request.mensaje())
                .estado(EstadoPostulacion.PENDIENTE)
                .build();
        postulacionSolicitudRepository.save(postulacion);

        return armarResponse(solicitud);
    }

    @Override
    public SolicitudPartidoResponse aceptarPostulacion(Long solicitudId, Long postulacionId, String creadorEmail) {
        log.info("Aceptando postulación ID {} de solicitud ID {}", postulacionId, solicitudId);
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(solicitudId);
        validarEsCreador(solicitud, creadorEmail);

        PostulacionSolicitud postulacion = postulacionSolicitudRepository.findByIdAndSolicitudId(postulacionId, solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación no encontrada con ID: " + postulacionId));

        if (postulacion.getEstado() != EstadoPostulacion.PENDIENTE) {
            throw new EstadoInvalidoException("Esta postulación ya fue resuelta");
        }
        if (solicitud.getEstado() != EstadoSolicitud.ABIERTA) {
            throw new EstadoInvalidoException("La solicitud ya no está abierta");
        }

        postulacion.setEstado(EstadoPostulacion.ACEPTADA);
        postulacionSolicitudRepository.save(postulacion);

        long aceptadas = postulacionSolicitudRepository.findBySolicitudIdAndEstado(solicitudId, EstadoPostulacion.ACEPTADA).size();
        if (aceptadas >= solicitud.getCantidadJugadoresFaltantes()) {
            solicitud.setEstado(EstadoSolicitud.COMPLETA);
            solicitudPartidoRepository.save(solicitud);

            List<PostulacionSolicitud> pendientes = postulacionSolicitudRepository.findBySolicitudIdAndEstado(solicitudId, EstadoPostulacion.PENDIENTE);
            pendientes.forEach(p -> p.setEstado(EstadoPostulacion.RECHAZADA));
            postulacionSolicitudRepository.saveAll(pendientes);
        }

        return armarResponse(solicitud);
    }

    @Override
    public SolicitudPartidoResponse rechazarPostulacion(Long solicitudId, Long postulacionId, String creadorEmail) {
        log.info("Rechazando postulación ID {} de solicitud ID {}", postulacionId, solicitudId);
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(solicitudId);
        validarEsCreador(solicitud, creadorEmail);

        PostulacionSolicitud postulacion = postulacionSolicitudRepository.findByIdAndSolicitudId(postulacionId, solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación no encontrada con ID: " + postulacionId));

        if (postulacion.getEstado() != EstadoPostulacion.PENDIENTE) {
            throw new EstadoInvalidoException("Esta postulación ya fue resuelta");
        }

        postulacion.setEstado(EstadoPostulacion.RECHAZADA);
        postulacionSolicitudRepository.save(postulacion);

        return armarResponse(solicitud);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPartidoResponse> misSolicitudes(String usuarioEmail) {
        return solicitudPartidoRepository.findByCreadorEmailOrderByCreatedAtDesc(usuarioEmail).stream()
                .map(this::armarResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPartidoResponse> misPostulaciones(String usuarioEmail) {
        return postulacionSolicitudRepository.findByJugadorEmailOrderByCreatedAtDesc(usuarioEmail).stream()
                .map(PostulacionSolicitud::getSolicitud)
                .map(this::armarResponse)
                .toList();
    }

    private void validarHorarioHabilitado(Cancha cancha, LocalDateTime fechaHoraPropuesta) {
        DayOfWeek dia = fechaHoraPropuesta.getDayOfWeek();
        LocalTime hora = fechaHoraPropuesta.toLocalTime();

        boolean existeFranja = franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(cancha.getId()).stream()
                .anyMatch(f -> diasAplicables(f).contains(dia)
                        && !hora.isBefore(f.getHoraInicio())
                        && hora.isBefore(f.getHoraFin()));

        if (!existeFranja) {
            throw new DatosInvalidosException("La cancha seleccionada no tiene un horario habilitado para el día y la hora propuestos");
        }
    }

    private Set<DayOfWeek> diasAplicables(FranjaHoraria franja) {
        if (franja.getDiasAplicables() == null || franja.getDiasAplicables().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(franja.getDiasAplicables().split(","))
                .map(String::trim)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    private void validarEsCreador(SolicitudPartido solicitud, String usuarioEmail) {
        if (!solicitud.getCreador().getEmail().equalsIgnoreCase(usuarioEmail)) {
            throw new AccessDeniedException("No tiene permisos para operar sobre esta solicitud");
        }
    }

    private SolicitudPartidoResponse armarResponse(SolicitudPartido solicitud) {
        List<PostulacionSolicitudResponse> postulaciones = postulacionSolicitudRepository.findBySolicitudId(solicitud.getId()).stream()
                .map(postulacionSolicitudMapper::toResponse)
                .toList();
        return solicitudPartidoMapper.toResponse(solicitud, postulaciones);
    }

    private SolicitudPartido obtenerSolicitudOrThrow(Long id) {
        return solicitudPartidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con ID: " + id));
    }
}
