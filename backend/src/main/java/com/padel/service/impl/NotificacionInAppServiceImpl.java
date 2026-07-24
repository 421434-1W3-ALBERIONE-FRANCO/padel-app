package com.padel.service.impl;

import com.padel.domain.entity.Notificacion;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.response.NotificacionResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.NotificacionMapper;
import com.padel.repository.NotificacionRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.NotificacionInAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificacionInAppServiceImpl implements NotificacionInAppService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionMapper notificacionMapper;

    @Override
    @Transactional
    public void crear(Usuario destinatario, TipoNotificacion tipo, String titulo, String mensaje, Long referenciaId) {
        Notificacion notificacion = Notificacion.builder()
                .usuario(destinatario)
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .referenciaId(referenciaId)
                .leida(false)
                .build();
        notificacionRepository.save(notificacion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionResponse> listarMias(String email) {
        Usuario usuario = obtenerUsuario(email);
        return notificacionRepository.findByUsuarioIdOrderByCreatedAtDesc(usuario.getId()).stream()
                .map(notificacionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNoLeidas(String email) {
        Usuario usuario = obtenerUsuario(email);
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuario.getId());
    }

    @Override
    @Transactional
    public void marcarLeida(Long id, String email) {
        Usuario usuario = obtenerUsuario(email);
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada con ID: " + id));

        if (!notificacion.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Notificación no encontrada para el usuario especificado");
        }

        notificacion.setLeida(true);
        notificacion.setLeidaEn(LocalDateTime.now());
        notificacionRepository.save(notificacion);
    }

    @Override
    @Transactional
    public void marcarTodasLeidas(String email) {
        Usuario usuario = obtenerUsuario(email);
        notificacionRepository.marcarTodasLeidas(usuario.getId(), LocalDateTime.now());
    }

    private Usuario obtenerUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }
}
