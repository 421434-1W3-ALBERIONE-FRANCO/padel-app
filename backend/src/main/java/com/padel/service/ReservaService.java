package com.padel.service;

import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.ReservaResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReservaService {
    ReservaResponse crearReserva(ReservaRequest request, String usuarioEmail);
    ReservaResponse cancelarReserva(Long id, String usuarioEmail, String motivo);
    ReservaResponse obtenerPorId(Long id, String usuarioEmail);
    List<ReservaResponse> obtenerMisReservas(String usuarioEmail);
    List<ReservaResponse> obtenerTodas();
    List<ReservaResponse> obtenerPorFecha(LocalDate fecha);
    void limpiarReservasExpiradas();
    void completarReservasFinalizadas();
    ReservaResponse confirmarReserva(Long id);
    void notificarReservasDeHoy();
}
