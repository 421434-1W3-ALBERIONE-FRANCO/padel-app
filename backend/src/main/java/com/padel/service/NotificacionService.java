package com.padel.service;

import com.padel.domain.entity.Reserva;

public interface NotificacionService {
    void enviarConfirmacion(Reserva reserva);
    void enviarRecordatorio(Reserva reserva);
    void enviarCancelacion(Reserva reserva);
}
