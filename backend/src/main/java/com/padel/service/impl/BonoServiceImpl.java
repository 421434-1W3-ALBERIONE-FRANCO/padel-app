package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoBono;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoNotificacion;
import com.padel.dto.request.CrearBonoRequest;
import com.padel.dto.response.BonoResponse;
import com.padel.dto.response.PagoResponse;
import com.padel.exception.BonoNoDisponibleException;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.OperacionNoPermitidaException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.BonoMapper;
import com.padel.mapper.PagoMapper;
import com.padel.repository.*;
import com.padel.security.ControlAcceso;
import com.padel.service.BonoService;
import com.padel.service.ComisionService;
import com.padel.service.ConfiguracionSistemaService;
import com.padel.service.NotificacionInAppService;
import com.padel.service.ReservaService;
import com.padel.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BonoServiceImpl implements BonoService {

    private final BonoRepository bonoRepository;
    private final UsoBonoRepository usoBonoRepository;
    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaService reservaService;
    private final BonoMapper bonoMapper;
    private final PagoMapper pagoMapper;
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final NotificacionInAppService notificacionInAppService;
    private final ComisionService comisionService;
    private final ControlAcceso controlAcceso;
    private final TipoBonoRepository tipoBonoRepository;

    @Override
    public BonoResponse asignarBono(CrearBonoRequest request, String staffEmail) {
        Usuario staff = usuarioRepository.findByEmail(EmailUtil.normalizar(staffEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        TipoBono tipo = tipoBonoRepository.findById(request.tipoBonoId())
                .filter(TipoBono::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de bono no encontrado o dado de baja"));

        // Un bono sin cargo es plata que el club resigna: solo lo decide un administrador y con motivo.
        boolean sinCargo = request.sinCargo() || tipo.getPrecio().signum() == 0;
        if (request.sinCargo()) {
            if (staff.getRol() != RolUsuario.ADMIN) {
                throw new OperacionNoPermitidaException("Solo un administrador puede asignar bonos sin cargo");
            }
            if (request.motivoSinCargo() == null || request.motivoSinCargo().isBlank()) {
                throw new DatosInvalidosException("Indicá el motivo del bono sin cargo");
            }
        }
        // La venta entra a la caja: hace falta saber cómo se cobró.
        if (!sinCargo && (request.metodo() == null || !request.metodo().esCobroEnMostrador())) {
            throw new DatosInvalidosException("Indicá cómo se cobró el bono: en efectivo o con tarjeta");
        }

        Usuario usuario = usuarioRepository.findByEmail(EmailUtil.normalizar(request.usuarioEmail()))
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos un jugador habilitado con ese email"));

        BigDecimal precio = sinCargo ? BigDecimal.ZERO : tipo.getPrecio();
        Bono bono = bonoRepository.save(Bono.builder()
                .usuario(usuario)
                .tipoBono(tipo)
                .tipo(tipo.getNombre())
                .horasTotales(tipo.getHoras())
                .horasUsadas(0)
                .precioPagado(precio)
                .fechaVencimiento(LocalDate.now().plusDays(tipo.getDiasVigencia()))
                .estado(EstadoBono.ACTIVO)
                .vendidoPor(staff)
                .motivoSinCargo(request.sinCargo() ? request.motivoSinCargo().trim() : null)
                .build());
        log.info("Bono ID {} ({}) asignado al usuario ID {} por el staff ID {}{}", bono.getId(), tipo.getNombre(),
                usuario.getId(), staff.getId(), sinCargo ? " sin cargo" : "");

        if (!sinCargo) {
            pagoRepository.save(Pago.builder()
                    .bono(bono)
                    .usuario(usuario)
                    .monto(precio)
                    .metodo(request.metodo())
                    .estado(EstadoPago.APROBADO)
                    .concepto(ConceptoPago.BONO)
                    .atendidoPor(staff)
                    .build());
            // La comisión se calcula sobre el precio del catálogo: ya no se puede inflar cambiando el precio.
            if (staff.getRol() == RolUsuario.RECEPCIONISTA && configuracionSistemaService.obtenerEntidad().isComisionesHabilitadas()) {
                comisionService.registrarComisionBono(staff, bono);
            }
        }
        return bonoMapper.toResponse(bono);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonoResponse> obtenerMisBonos(String usuarioEmail) {
        
        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        List<Bono> bonos = bonoRepository.findByUsuarioIdOrderByFechaVencimientoDesc(usuario.getId());
        return bonos.stream()
                .map(bonoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BonoResponse obtenerPorId(Long id, String usuarioEmail) {
        Bono bono = bonoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bono no encontrado con ID: " + id));

        Usuario usuario = usuarioRepository.findByEmail(EmailUtil.normalizar(usuarioEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        controlAcceso.exigirDuenoOStaff(usuario, bono.getUsuario(), "Bono");

        return bonoMapper.toResponse(bono);
    }

    @Override
    public PagoResponse usarBono(Long reservaId, String usuarioEmail) {
        // Bloqueo de la reserva y de los bonos del jugador: dos pagos con bono en paralelo (doble click o dos
        // reservas a la vez) descontaban las mismas horas dos veces.
        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
        Usuario usuario = usuarioRepository.findByEmail(EmailUtil.normalizar(usuarioEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        controlAcceso.exigirDuenoOStaff(usuario, reserva.getUsuario(), "Reserva");
        log.info("Pago con bono de la reserva ID {} pedido por el usuario ID {}", reservaId, usuario.getId());

        if (reserva.getEstadoReserva() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReservaNoModificableException("La reserva no se encuentra pendiente de pago. Estado actual: " + reserva.getEstadoReserva());
        }
        if (pagoRepository.existsByReservaIdAndEstadoAndConcepto(reservaId, EstadoPago.APROBADO, ConceptoPago.TURNO)) {
            throw new EstadoInvalidoException("El turno ya tiene un cobro registrado");
        }

        // RangoHorario valida fin > inicio: una reserva que cruzaba la medianoche daba minutos negativos y el
        // bono terminaba GANANDO horas en lugar de descontarlas.
        long minutos = reserva.rango().minutos();
        int horasADescontar = (int) Math.ceil(minutos / 60.0);

        LocalDate hoy = LocalDate.now();
        Bono bonoSeleccionado = null;
        for (Bono b : bonoRepository.findActivosParaUsar(reserva.getUsuario().getId())) {
            if (b.estaVencidoAl(hoy)) {
                // Por si el scheduler de vencimiento todavía no corrió.
                b.setEstado(EstadoBono.VENCIDO);
                bonoRepository.save(b);
                continue;
            }
            if (b.saldoHoras() >= horasADescontar) {
                bonoSeleccionado = b;
                break;
            }
        }
        if (bonoSeleccionado == null) {
            throw new BonoNoDisponibleException("No posees un bono activo con saldo de horas suficiente (" + horasADescontar + " hs requeridas)");
        }

        bonoSeleccionado.descontarHoras(horasADescontar);
        bonoRepository.save(bonoSeleccionado);
        usoBonoRepository.save(UsoBono.builder()
                .bono(bonoSeleccionado)
                .reserva(reserva)
                .horasDescontadas(horasADescontar)
                .build());

        Pago pago = pagoRepository.save(Pago.builder()
                .reserva(reserva)
                .usuario(reserva.getUsuario())
                .monto(reserva.getPrecioTotal())
                .metodo(MetodoPago.BONO)
                .estado(EstadoPago.APROBADO)
                .concepto(ConceptoPago.TURNO)
                .build());

        // Confirma la reserva (libera el lock del horario y avisa al jugador).
        reservaService.confirmarReserva(reservaId);
        log.info("Bono ID {}: {} hs descontadas por la reserva ID {}", bonoSeleccionado.getId(), horasADescontar, reservaId);
        return pagoMapper.toResponse(pago);
    }

    @Override
    public void expirarBonosVencidos() {
        LocalDate hoy = LocalDate.now();
        log.info("Ejecutando proceso de vencimiento automático para fecha {}", hoy);

        List<Bono> vencidos = bonoRepository.findByEstadoAndFechaVencimientoBefore(EstadoBono.ACTIVO, hoy);
        for (Bono b : vencidos) {
            b.setEstado(EstadoBono.VENCIDO);
            bonoRepository.save(b);
            log.info("Bono ID {} del usuario ID {} marcado como VENCIDO", b.getId(), b.getUsuario().getId());
        }
    }

    @Override
    public void notificarVencimientosProximos() {
        int diasAviso = configuracionSistemaService.obtenerEntidad().getDiasAvisoVencimientoBono();
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusDays(diasAviso);
        log.info("Buscando bonos activos que vencen entre {} y {} sin aviso enviado", hoy, limite);

        List<Bono> porVencer = bonoRepository.findByEstadoAndFechaVencimientoBetweenAndAvisoVencimientoEnviadoFalse(
                EstadoBono.ACTIVO, hoy, limite);

        for (Bono b : porVencer) {
            int saldoDisponible = b.getHorasTotales() - b.getHorasUsadas();
            notificacionInAppService.crear(
                    b.getUsuario(),
                    TipoNotificacion.VENCIMIENTO_BONO,
                    "Tu bono está por vencer",
                    String.format("Tu bono %s vence el %s. Te quedan %d horas disponibles por usar.",
                            b.getTipo(), b.getFechaVencimiento(), saldoDisponible),
                    b.getId()
            );
            b.setAvisoVencimientoEnviado(true);
            bonoRepository.save(b);
        }
        log.info("Avisos de vencimiento de bono enviados: {}", porVencer.size());
    }
}
