package com.padel.service;

import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.evento.OrigenCancelacion;
import com.padel.dto.response.PagoResponse;

import java.util.List;

/**
 * Política de reintegros al cancelar un turno ya cobrado: devuelve horas de bono, encola la devolución
 * por MercadoPago o deja el reintegro pendiente de entrega en el mostrador.
 */
public interface ReintegroService {

    /** Aplica la política a una reserva que se acaba de cancelar. Devuelve el texto para avisarle al jugador. */
    String reintegrarCancelacion(Reserva reserva, OrigenCancelacion origen);

    /** Un pago de MercadoPago aprobado para una reserva que ya no estaba vigente: se devuelve completo. */
    void reintegrarPagoTardio(Pago pago);

    /** Intenta devolver por MercadoPago los reintegros encolados (lo invoca un proceso programado). */
    void procesarReintegrosAutomaticos();

    List<PagoResponse> listarPendientes();

    /** El staff registra que entregó el reintegro (en efectivo, tarjeta o desde el panel de MercadoPago). */
    PagoResponse registrarEntrega(Long reintegroId, String staffEmail);
}
