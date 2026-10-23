package com.padel.service.impl;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.PostulacionSolicitud;
import com.padel.domain.entity.SolicitudPartido;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoPostulacion;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;
import com.padel.dto.request.PostulacionSolicitudRequest;
import com.padel.dto.request.SolicitudPartidoRequest;
import com.padel.dto.response.ContactoResponse;
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
import com.padel.util.EmailUtil;
import com.padel.util.FechaHoraUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
    private final FechaHoraUtil fechaHoraUtil;

    @Override
    public SolicitudPartidoResponse crear(SolicitudPartidoRequest request, String creadorEmail) {
        fechaHoraUtil.validarAnioRazonable(request.fechaHoraPropuesta().toLocalDate(), "la fecha propuesta");
        Usuario creador = buscarUsuario(creadorEmail);
        log.info("Creando solicitud de partido del usuario ID {} en categoría {}", creador.getId(), request.categoria());

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

        return armarRespuestas(List.of(solicitudPartidoRepository.save(solicitud)), creador).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPartidoResponse> buscar(TipoSolicitud tipoSolicitud, CategoriaJugador categoria, EstadoSolicitud estado,
                                                 String usuarioEmail) {
        EstadoSolicitud estadoFiltro = estado != null ? estado : EstadoSolicitud.ABIERTA;
        return armarRespuestas(solicitudPartidoRepository.buscar(estadoFiltro, categoria, tipoSolicitud), buscarUsuario(usuarioEmail));
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitudPartidoResponse obtenerPorId(Long id, String usuarioEmail) {
        return armarRespuestas(List.of(obtenerSolicitudOrThrow(id)), buscarUsuario(usuarioEmail)).get(0);
    }

    @Override
    public void cancelar(Long id, String usuarioEmail) {
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(id);
        validarEsCreador(solicitud, buscarUsuario(usuarioEmail));

        if (solicitud.getEstado() == EstadoSolicitud.CANCELADA) {
            throw new EstadoInvalidoException("La solicitud ya está cancelada");
        }
        solicitud.setEstado(EstadoSolicitud.CANCELADA);
        solicitudPartidoRepository.save(solicitud);
    }

    @Override
    public SolicitudPartidoResponse postularse(Long solicitudId, PostulacionSolicitudRequest request, String jugadorEmail) {
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(solicitudId);
        if (solicitud.getEstado() != EstadoSolicitud.ABIERTA) {
            throw new EstadoInvalidoException("La solicitud ya no admite postulaciones");
        }

        Usuario jugador = buscarUsuario(jugadorEmail);
        if (solicitud.getCreador().getId().equals(jugador.getId())) {
            throw new DatosInvalidosException("No podés postularte a tu propia solicitud");
        }
        if (postulacionSolicitudRepository.existsBySolicitudIdAndJugadorId(solicitudId, jugador.getId())) {
            throw new DatosInvalidosException("Ya te postulaste a esta solicitud");
        }

        postulacionSolicitudRepository.save(PostulacionSolicitud.builder()
                .solicitud(solicitud)
                .jugador(jugador)
                .mensaje(request.mensaje())
                .estado(EstadoPostulacion.PENDIENTE)
                .build());
        return armarRespuestas(List.of(solicitud), jugador).get(0);
    }

    @Override
    public SolicitudPartidoResponse aceptarPostulacion(Long solicitudId, Long postulacionId, String creadorEmail) {
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(solicitudId);
        Usuario creador = buscarUsuario(creadorEmail);
        validarEsCreador(solicitud, creador);

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
        return armarRespuestas(List.of(solicitud), creador).get(0);
    }

    @Override
    public SolicitudPartidoResponse rechazarPostulacion(Long solicitudId, Long postulacionId, String creadorEmail) {
        SolicitudPartido solicitud = obtenerSolicitudOrThrow(solicitudId);
        Usuario creador = buscarUsuario(creadorEmail);
        validarEsCreador(solicitud, creador);

        PostulacionSolicitud postulacion = postulacionSolicitudRepository.findByIdAndSolicitudId(postulacionId, solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Postulación no encontrada con ID: " + postulacionId));
        if (postulacion.getEstado() != EstadoPostulacion.PENDIENTE) {
            throw new EstadoInvalidoException("Esta postulación ya fue resuelta");
        }
        postulacion.setEstado(EstadoPostulacion.RECHAZADA);
        postulacionSolicitudRepository.save(postulacion);
        return armarRespuestas(List.of(solicitud), creador).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPartidoResponse> misSolicitudes(String usuarioEmail) {
        Usuario usuario = buscarUsuario(usuarioEmail);
        return armarRespuestas(solicitudPartidoRepository.findByCreadorIdOrderByCreatedAtDesc(usuario.getId()), usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPartidoResponse> misPostulaciones(String usuarioEmail) {
        Usuario usuario = buscarUsuario(usuarioEmail);
        List<SolicitudPartido> solicitudes = postulacionSolicitudRepository.findByJugadorIdOrderByCreatedAtDesc(usuario.getId()).stream()
                .map(PostulacionSolicitud::getSolicitud)
                .toList();
        return armarRespuestas(solicitudes, usuario);
    }

    private void validarHorarioHabilitado(Cancha cancha, LocalDateTime fechaHoraPropuesta) {
        DayOfWeek dia = fechaHoraPropuesta.getDayOfWeek();
        LocalTime hora = fechaHoraPropuesta.toLocalTime();

        boolean existeFranja = franjaHorariaRepository.findByCanchaIdAndCanchaActivaTrue(cancha.getId()).stream()
                .anyMatch(f -> f.aplicaEn(dia) && !hora.isBefore(f.getHoraInicio()) && hora.isBefore(f.getHoraFin()));
        if (!existeFranja) {
            throw new DatosInvalidosException("La cancha seleccionada no tiene un horario habilitado para el día y la hora propuestos");
        }
    }

    // Se compara por id: comparar emails sin distinguir mayúsculas dejaba operar a una cuenta "JUAN@x.com"
    // sobre las solicitudes de "juan@x.com".
    private void validarEsCreador(SolicitudPartido solicitud, Usuario usuario) {
        if (!solicitud.getCreador().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permisos para operar sobre esta solicitud");
        }
    }

    /**
     * Arma las respuestas según quién mira: el creador ve todas las postulaciones (y el contacto de los
     * aceptados); un postulante ve solo la suya (y el contacto del creador si lo aceptaron); el resto, ninguna.
     * Las postulaciones de todas las solicitudes se traen en una sola consulta.
     */
    private List<SolicitudPartidoResponse> armarRespuestas(List<SolicitudPartido> solicitudes, Usuario viewer) {
        if (solicitudes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PostulacionSolicitud>> porSolicitud = postulacionSolicitudRepository
                .findConJugadorBySolicitudIdIn(solicitudes.stream().map(SolicitudPartido::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getSolicitud().getId()));

        return solicitudes.stream().map(solicitud -> {
            List<PostulacionSolicitud> todas = porSolicitud.getOrDefault(solicitud.getId(), List.of());
            boolean esCreador = solicitud.getCreador().getId().equals(viewer.getId());
            boolean aceptado = todas.stream().anyMatch(p -> p.getJugador().getId().equals(viewer.getId())
                    && p.getEstado() == EstadoPostulacion.ACEPTADA);

            List<PostulacionSolicitudResponse> visibles = todas.stream()
                    .filter(p -> esCreador || p.getJugador().getId().equals(viewer.getId()))
                    .map(p -> postulacionSolicitudMapper.toResponse(p,
                            esCreador && p.getEstado() == EstadoPostulacion.ACEPTADA ? contacto(p.getJugador()) : null))
                    .toList();
            ContactoResponse contactoCreador = esCreador || aceptado ? contacto(solicitud.getCreador()) : null;
            return solicitudPartidoMapper.toResponse(solicitud, contactoCreador, visibles, todas.size());
        }).toList();
    }

    private static ContactoResponse contacto(Usuario usuario) {
        return new ContactoResponse(usuario.getEmail(), usuario.getTelefono());
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private SolicitudPartido obtenerSolicitudOrThrow(Long id) {
        return solicitudPartidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con ID: " + id));
    }
}
