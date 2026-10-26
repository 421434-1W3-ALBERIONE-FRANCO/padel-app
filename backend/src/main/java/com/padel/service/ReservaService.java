package com.padel.service;

import com.padel.domain.enums.EstadoReserva;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.PaginaResponse;
import com.padel.dto.response.ReservaResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ReservaService {
    ReservaResponse crearReserva(ReservaRequest request, String usuarioEmail);

    ReservaResponse cancelarReserva(Long id, String usuarioEmail, String motivo);

    /** Cancelación iniciada por el club (p. ej. un bloqueo de cancha): siempre con reintegro y aviso al jugador. */
    void cancelarPorClub(Long id, String motivo);

    ReservaResponse obtenerPorId(Long id, String usuarioEmail);

    List<ReservaResponse> obtenerMisReservas(String usuarioEmail);

    /** Historial de reservas para el staff, filtrado y paginado. */
    PaginaResponse<ReservaResponse> buscar(LocalDate desde, LocalDate hasta, EstadoReserva estado, String texto, Pageable pageable);

    List<ReservaResponse> obtenerPorFecha(LocalDate fecha);

    void limpiarReservasExpiradas();

    void completarReservasFinalizadas();

    ReservaResponse confirmarReserva(Long id);

    void notificarReservasDeHoy();
}
