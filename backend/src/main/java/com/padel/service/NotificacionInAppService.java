package com.padel.service;

import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.response.NotificacionResponse;

import java.util.List;

public interface NotificacionInAppService {
    void crear(Usuario destinatario, TipoNotificacion tipo, String titulo, String mensaje, Long referenciaId);
    List<NotificacionResponse> listarMias(String email);
    long contarNoLeidas(String email);
    void marcarLeida(Long id, String email);
    void marcarTodasLeidas(String email);
}
