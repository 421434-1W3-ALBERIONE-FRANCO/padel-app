package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoBono;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CrearBonoRequest;
import com.padel.dto.response.BonoResponse;
import com.padel.dto.response.PagoResponse;
import com.padel.exception.BonoNoDisponibleException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.BonoMapper;
import com.padel.mapper.PagoMapper;
import com.padel.repository.*;
import com.padel.service.BonoService;
import com.padel.service.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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

    @Override
    public BonoResponse asignarBono(CrearBonoRequest request) {
        log.info("Asignando bono de tipo {} al usuario {}", request.tipo(), request.usuarioEmail());

        Usuario usuario = usuarioRepository.findByEmail(request.usuarioEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.usuarioEmail()));

        LocalDate hoy = LocalDate.now();
        EstadoBono estado = request.fechaVencimiento().isBefore(hoy) ? EstadoBono.VENCIDO : EstadoBono.ACTIVO;

        Bono bono = Bono.builder()
                .usuario(usuario)
                .tipo(request.tipo())
                .horasTotales(request.horasTotales())
                .horasUsadas(0)
                .precioPagado(request.precioPagado())
                .fechaVencimiento(request.fechaVencimiento())
                .estado(estado)
                .build();

        Bono saved = bonoRepository.save(bono);
        log.info("Bono ID {} asignado correctamente", saved.getId());
        return bonoMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonoResponse> obtenerMisBonos(String usuarioEmail) {
        log.info("Obteniendo bonos para usuario: {}", usuarioEmail);

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

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Validar permisos
        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !bono.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Bono no encontrado para el usuario especificado");
        }

        return bonoMapper.toResponse(bono);
    }

    @Override
    public PagoResponse usarBono(Long reservaId, String usuarioEmail) {
        log.info("Usuario {} intentando pagar reserva ID {} con bono", usuarioEmail, reservaId);

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        // Validar permisos: el dueño o administración
        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !reserva.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Reserva no encontrada para el usuario especificado");
        }

        // Validar estado de la reserva
        if (reserva.getEstadoReserva() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReservaNoModificableException("La reserva no se encuentra pendiente de pago. Estado actual: " + reserva.getEstadoReserva());
        }

        // Calcular duración en minutos dinámicamente y redondear a horas hacia arriba
        long minutos = Duration.between(reserva.getHoraInicio(), reserva.getHoraFin()).toMinutes();
        int horasADescontar = (int) Math.ceil((double) minutos / 60.0);
        log.info("Duración de reserva: {} minutos. Horas a descontar: {}", minutos, horasADescontar);

        // Obtener bonos activos del usuario dueño de la reserva ordenados por vencimiento (vence primero, se usa primero)
        List<Bono> bonosActivos = bonoRepository.findByUsuarioIdAndEstadoOrderByFechaVencimientoAsc(reserva.getUsuario().getId(), EstadoBono.ACTIVO);

        Bono bonoSeleccionado = null;
        for (Bono b : bonosActivos) {
            // Verificar si el bono no ha vencido manualmente (por si el scheduler aún no corrió)
            if (b.getFechaVencimiento().isBefore(LocalDate.now())) {
                b.setEstado(EstadoBono.VENCIDO);
                bonoRepository.save(b);
                continue;
            }
            int saldoDisponible = b.getHorasTotales() - b.getHorasUsadas();
            if (saldoDisponible >= horasADescontar) {
                bonoSeleccionado = b;
                break;
            }
        }

        if (bonoSeleccionado == null) {
            throw new BonoNoDisponibleException("No posees un bono activo con saldo de horas suficiente (" + horasADescontar + " hs requeridas)");
        }

        // Descontar horas del bono
        bonoSeleccionado.setHorasUsadas(bonoSeleccionado.getHorasUsadas() + horasADescontar);
        if (bonoSeleccionado.getHorasUsadas().equals(bonoSeleccionado.getHorasTotales())) {
            bonoSeleccionado.setEstado(EstadoBono.AGOTADO);
        }
        bonoRepository.save(bonoSeleccionado);
        log.info("Horas descontadas del Bono ID {}. Nuevas horas usadas: {}/{}", 
                bonoSeleccionado.getId(), bonoSeleccionado.getHorasUsadas(), bonoSeleccionado.getHorasTotales());

        // Registrar auditoría UsoBono
        UsoBono usoBono = UsoBono.builder()
                .bono(bonoSeleccionado)
                .reserva(reserva)
                .horasDescontadas(horasADescontar)
                .build();
        usoBonoRepository.save(usoBono);

        // Crear Pago (monto = nominal de la reserva)
        Pago pago = Pago.builder()
                .reserva(reserva)
                .usuario(reserva.getUsuario())
                .monto(reserva.getPrecioTotal())
                .metodo(MetodoPago.BONO)
                .estado(EstadoPago.APROBADO)
                .build();
        Pago savedPago = pagoRepository.save(pago);

        // Confirmar la reserva (esto también libera el Redis Lock y dispara la confirmación por WhatsApp)
        reservaService.confirmarReserva(reservaId);

        log.info("Pago por bono registrado exitosamente con ID {}", savedPago.getId());
        return pagoMapper.toResponse(savedPago);
    }

    @Override
    public void expirarBonosVencidos() {
        LocalDate hoy = LocalDate.now();
        log.info("Ejecutando proceso de vencimiento automático para fecha {}", hoy);

        List<Bono> vencidos = bonoRepository.findByEstadoAndFechaVencimientoBefore(EstadoBono.ACTIVO, hoy);
        for (Bono b : vencidos) {
            b.setEstado(EstadoBono.VENCIDO);
            bonoRepository.save(b);
            log.info("Bono ID {} del usuario {} marcado como VENCIDO", b.getId(), b.getUsuario().getEmail());
        }
    }
}
