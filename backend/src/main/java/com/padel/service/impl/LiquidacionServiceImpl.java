package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoComision;
import com.padel.domain.enums.EstadoSesionTrabajo;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoMovimientoBancoHoras;
import com.padel.dto.request.LiquidacionRequest;
import com.padel.dto.response.LiquidacionResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.LiquidacionSueldoMapper;
import com.padel.repository.*;
import com.padel.service.ConfiguracionSistemaService;
import com.padel.service.LiquidacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiquidacionServiceImpl implements LiquidacionService {

    private final LiquidacionSueldoRepository liquidacionSueldoRepository;
    private final SesionTrabajoRepository sesionTrabajoRepository;
    private final MovimientoBancoHorasRepository movimientoBancoHorasRepository;
    private final BancoHorasRepository bancoHorasRepository;
    private final ComisionRepository comisionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final LiquidacionSueldoMapper liquidacionSueldoMapper;

    @Override
    @Transactional
    public LiquidacionResponse liquidar(LiquidacionRequest request, String adminEmail) {
        if (request.hasta().isBefore(request.desde())) {
            throw new DatosInvalidosException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }

        Usuario empleado = usuarioRepository.findById(request.empleadoId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con ID: " + request.empleadoId()));

        if (empleado.getRol() != RolUsuario.ADMIN && empleado.getRol() != RolUsuario.RECEPCIONISTA) {
            throw new DatosInvalidosException("Solo se puede liquidar a personal ADMIN o RECEPCIONISTA");
        }

        Usuario admin = usuarioRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + adminEmail));

        LocalDateTime inicio = request.desde().atStartOfDay();
        LocalDateTime fin = request.hasta().atTime(LocalTime.MAX);

        // 1. Minutos trabajados (sesiones cerradas del período)
        int totalMinutosTrabajados = sesionTrabajoRepository
                .findByEmpleadoIdAndEstadoAndInicioBetween(empleado.getId(), EstadoSesionTrabajo.CERRADA, inicio, fin).stream()
                .mapToInt(s -> s.getMinutosTrabajados() != null ? s.getMinutosTrabajados() : 0)
                .sum();

        // 2. Banco de horas acreditado no liquidado del período
        List<MovimientoBancoHoras> movimientos = movimientoBancoHorasRepository
                .findByEmpleadoIdAndLiquidadoFalseAndCreatedAtBetween(empleado.getId(), inicio, fin);
        int totalMinutosBancoHoras = movimientos.stream().mapToInt(MovimientoBancoHoras::getMinutos).sum();

        // 3. Comisiones pendientes del período (solo recepción con comisiones habilitadas)
        BigDecimal totalComisiones = BigDecimal.ZERO;
        List<Comision> comisiones = List.of();
        boolean comisionesHabilitadas = configuracionSistemaService.obtenerEntidad().isComisionesHabilitadas();
        if (empleado.getRol() == RolUsuario.RECEPCIONISTA && comisionesHabilitadas) {
            comisiones = comisionRepository.findByRecepcionistaIdAndEstadoAndCreatedAtBetween(
                    empleado.getId(), EstadoComision.PENDIENTE, inicio, fin);
            totalComisiones = comisiones.stream()
                    .map(Comision::getMontoComision)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 4. Persistir el snapshot inmutable
        LiquidacionSueldo liquidacion = LiquidacionSueldo.builder()
                .empleado(empleado)
                .periodoDesde(request.desde())
                .periodoHasta(request.hasta())
                .totalMinutosTrabajados(totalMinutosTrabajados)
                .totalMinutosBancoHoras(totalMinutosBancoHoras)
                .totalComisiones(totalComisiones)
                .generadaPor(admin)
                .generadaEn(LocalDateTime.now())
                .build();
        LiquidacionSueldo guardada = liquidacionSueldoRepository.save(liquidacion);

        // Marcar movimientos como liquidados y descontar el saldo del banco de horas con un movimiento negativo.
        if (!movimientos.isEmpty()) {
            for (MovimientoBancoHoras m : movimientos) {
                m.setLiquidado(true);
                m.setLiquidacion(guardada);
            }
            movimientoBancoHorasRepository.saveAll(movimientos);

            bancoHorasRepository.findByEmpleadoId(empleado.getId()).ifPresent(banco -> {
                banco.setSaldoMinutos(banco.getSaldoMinutos() - totalMinutosBancoHoras);
                bancoHorasRepository.save(banco);
            });

            MovimientoBancoHoras movimientoLiquidacion = MovimientoBancoHoras.builder()
                    .empleado(empleado)
                    .minutos(-totalMinutosBancoHoras)
                    .tipo(TipoMovimientoBancoHoras.LIQUIDACION)
                    .liquidado(true)
                    .liquidacion(guardada)
                    .build();
            movimientoBancoHorasRepository.save(movimientoLiquidacion);
        }

        // Marcar comisiones como pagadas
        if (!comisiones.isEmpty()) {
            for (Comision c : comisiones) {
                c.setEstado(EstadoComision.PAGADA);
                c.setLiquidacion(guardada);
            }
            comisionRepository.saveAll(comisiones);
        }

        log.info("Liquidación ID {} generada para empleado {} por admin {}: {} min trabajados, {} min banco, ${} comisiones",
                guardada.getId(), empleado.getEmail(), adminEmail, totalMinutosTrabajados, totalMinutosBancoHoras, totalComisiones);

        return liquidacionSueldoMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiquidacionResponse> historialPorEmpleado(Long empleadoId) {
        return liquidacionSueldoRepository.findByEmpleadoIdOrderByGeneradaEnDesc(empleadoId).stream()
                .map(liquidacionSueldoMapper::toResponse)
                .toList();
    }
}
