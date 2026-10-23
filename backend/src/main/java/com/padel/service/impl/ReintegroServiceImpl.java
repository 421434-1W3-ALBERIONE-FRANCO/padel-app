package com.padel.service.impl;

import com.padel.config.ReservasProperties;
import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.UsoBono;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.domain.evento.OrigenCancelacion;
import com.padel.dto.response.PagoResponse;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.integracion.pagos.PasarelaPagos;
import com.padel.integracion.pagos.PasarelaPagosException;
import com.padel.mapper.PagoMapper;
import com.padel.repository.BonoRepository;
import com.padel.repository.PagoRepository;
import com.padel.repository.UsoBonoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.NotificacionInAppService;
import com.padel.service.ReintegroService;
import com.padel.util.MonedaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReintegroServiceImpl implements ReintegroService {

    /** Intentos automáticos por MercadoPago antes de dejar el reintegro para resolver a mano. */
    static final int MAX_INTENTOS_AUTOMATICOS = 3;

    private final PagoRepository pagoRepository;
    private final UsoBonoRepository usoBonoRepository;
    private final BonoRepository bonoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasarelaPagos pasarelaPagos;
    private final NotificacionInAppService notificacionInAppService;
    private final ReservasProperties reservasProperties;
    private final PagoMapper pagoMapper;

    @Override
    public String reintegrarCancelacion(Reserva reserva, OrigenCancelacion origen) {
        if (origen == OrigenCancelacion.FALTA_DE_PAGO) {
            return "";
        }
        List<Pago> cobros = pagoRepository.findAllByReservaIdAndEstadoAndConcepto(
                reserva.getId(), EstadoPago.APROBADO, ConceptoPago.TURNO);
        if (cobros.isEmpty()) {
            return "";
        }
        if (!correspondeReintegro(reserva, origen)) {
            return String.format(" Como cancelaste con menos de %d horas de anticipación, el turno no tiene reintegro.",
                    reservasProperties.getHorasMinimasCancelacionConReintegro());
        }

        List<String> partes = new ArrayList<>();
        for (Pago cobro : cobros) {
            if (pagoRepository.existsByPagoOriginalId(cobro.getId())) {
                continue; // Ya tiene su reintegro registrado.
            }
            partes.add(switch (cobro.getMetodo()) {
                case BONO -> devolverHorasDeBono(reserva, cobro);
                case MERCADOPAGO -> encolarReintegroMercadoPago(cobro);
                default -> registrarReintegroEnMostrador(cobro);
            });
        }
        return partes.isEmpty() ? "" : " " + String.join(" ", partes);
    }

    @Override
    public void reintegrarPagoTardio(Pago pago) {
        if (pagoRepository.existsByPagoOriginalId(pago.getId())) {
            return;
        }
        String detalle = encolarReintegroMercadoPago(pago);
        String fecha = pago.getReserva() != null ? " del " + pago.getReserva().getFecha() : "";
        notificacionInAppService.crear(pago.getUsuario(), TipoNotificacion.REINTEGRO,
                "Pago recibido para una reserva vencida",
                "Recibimos tu pago de " + MonedaUtil.formatear(pago.getMonto()) + ", pero la reserva" + fecha
                        + " ya no estaba vigente. " + detalle,
                pago.getReserva() != null ? pago.getReserva().getId() : null);
    }

    @Override
    public void procesarReintegrosAutomaticos() {
        if (!reservasProperties.isReintegroAutomaticoMercadoPago()) {
            return;
        }
        List<Pago> pendientes = pagoRepository.findTop20ByConceptoAndEstadoAndMetodoAndIntentosReintegroLessThanOrderByIdAsc(
                ConceptoPago.REINTEGRO, EstadoPago.PENDIENTE, MetodoPago.MERCADOPAGO, MAX_INTENTOS_AUTOMATICOS);
        for (Pago reintegro : pendientes) {
            Pago original = reintegro.getPagoOriginal();
            if (original == null || original.getMpPaymentId() == null) {
                reintegro.setIntentosReintegro(MAX_INTENTOS_AUTOMATICOS);
                avisarStaff(reintegro, "No hay un pago de MercadoPago asociado: resolverlo a mano.");
                continue;
            }
            reintegro.setIntentosReintegro(reintegro.getIntentosReintegro() + 1);
            try {
                pasarelaPagos.reembolsar(original.getMpPaymentId(), "reintegro-pago-" + original.getId());
                reintegro.setEstado(EstadoPago.APROBADO);
                log.info("Reintegro ID {} devuelto por MercadoPago (pago original ID {})", reintegro.getId(), original.getId());
                notificacionInAppService.crear(reintegro.getUsuario(), TipoNotificacion.REINTEGRO,
                        "Te devolvimos el pago",
                        "MercadoPago ya procesó la devolución de " + MonedaUtil.formatear(reintegro.getMonto().negate())
                                + ". Puede tardar unos días en verse según tu medio de pago.",
                        original.getReserva() != null ? original.getReserva().getId() : null);
            } catch (PasarelaPagosException e) {
                log.warn("Falló el intento {} de reintegro ID {}: {}", reintegro.getIntentosReintegro(), reintegro.getId(), e.getMessage());
                if (reintegro.getIntentosReintegro() >= MAX_INTENTOS_AUTOMATICOS) {
                    avisarStaff(reintegro, "MercadoPago no pudo procesar la devolución automática: hacela desde el panel de MercadoPago y registrala como entregada.");
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponse> listarPendientes() {
        return pagoRepository.findReintegrosPendientes().stream().map(pagoMapper::toResponse).toList();
    }

    @Override
    public PagoResponse registrarEntrega(Long reintegroId, String staffEmail) {
        Pago reintegro = pagoRepository.findByIdForUpdate(reintegroId)
                .filter(p -> p.getConcepto() == ConceptoPago.REINTEGRO)
                .orElseThrow(() -> new ResourceNotFoundException("Reintegro no encontrado con ID: " + reintegroId));
        if (reintegro.getEstado() != EstadoPago.PENDIENTE) {
            throw new EstadoInvalidoException("El reintegro ya fue registrado como entregado");
        }
        Usuario staff = usuarioRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        reintegro.setEstado(EstadoPago.APROBADO);
        reintegro.setAtendidoPor(staff);
        // Que el proceso automático no lo vuelva a intentar si era de MercadoPago.
        reintegro.setIntentosReintegro(MAX_INTENTOS_AUTOMATICOS);
        return pagoMapper.toResponse(pagoRepository.save(reintegro));
    }

    private boolean correspondeReintegro(Reserva reserva, OrigenCancelacion origen) {
        if (origen == OrigenCancelacion.CLUB) {
            return true;
        }
        LocalDateTime limite = reserva.inicio().minusHours(reservasProperties.getHorasMinimasCancelacionConReintegro());
        return !LocalDateTime.now().isAfter(limite);
    }

    private String devolverHorasDeBono(Reserva reserva, Pago cobro) {
        int horas = 0;
        for (UsoBono uso : usoBonoRepository.findByReservaId(reserva.getId())) {
            Bono bono = bonoRepository.findByIdForUpdate(uso.getBono().getId()).orElseThrow();
            bono.reintegrarHoras(uso.getHorasDescontadas(), LocalDate.now());
            bonoRepository.save(bono);
            horas += uso.getHorasDescontadas();
        }
        pagoRepository.save(nuevoReintegro(cobro, EstadoPago.APROBADO));
        return horas > 0 ? "Te devolvimos " + horas + (horas == 1 ? " hora" : " horas") + " a tu bono." : "";
    }

    private String encolarReintegroMercadoPago(Pago cobro) {
        pagoRepository.save(nuevoReintegro(cobro, EstadoPago.PENDIENTE));
        if (reservasProperties.isReintegroAutomaticoMercadoPago()) {
            return "Te vamos a devolver " + MonedaUtil.formatear(cobro.getMonto()) + " por MercadoPago.";
        }
        return "El club te va a devolver " + MonedaUtil.formatear(cobro.getMonto()) + " por MercadoPago.";
    }

    private String registrarReintegroEnMostrador(Pago cobro) {
        Pago reintegro = pagoRepository.save(nuevoReintegro(cobro, EstadoPago.PENDIENTE));
        avisarStaff(reintegro, "Entregalo en el mostrador y registralo en la caja.");
        return "Acercate al club para que te devuelvan " + MonedaUtil.formatear(cobro.getMonto()) + ".";
    }

    private Pago nuevoReintegro(Pago cobro, EstadoPago estado) {
        return Pago.builder()
                .reserva(cobro.getReserva())
                .bono(cobro.getBono())
                .usuario(cobro.getUsuario())
                .monto(cobro.getMonto().negate())
                .metodo(cobro.getMetodo())
                .estado(estado)
                .concepto(ConceptoPago.REINTEGRO)
                .pagoOriginal(cobro)
                .build();
    }

    private void avisarStaff(Pago reintegro, String instruccion) {
        BigDecimal monto = reintegro.getMonto().negate();
        String mensaje = String.format("Reintegro pendiente de %s a %s %s (%s). %s",
                MonedaUtil.formatear(monto), reintegro.getUsuario().getNombre(), reintegro.getUsuario().getApellido(),
                reintegro.getMetodo(), instruccion);
        Stream.concat(usuarioRepository.findByRol(RolUsuario.ADMIN).stream(),
                        usuarioRepository.findByRol(RolUsuario.RECEPCIONISTA).stream())
                .filter(Usuario::isActivo)
                .forEach(staff -> notificacionInAppService.crear(staff, TipoNotificacion.REINTEGRO_PENDIENTE,
                        "Reintegro pendiente", mensaje, reintegro.getId()));
    }
}
