package com.padel.service.impl;

import com.padel.domain.entity.Pago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import com.padel.dto.response.CajaDiariaResponse;
import com.padel.repository.PagoRepository;
import com.padel.service.ReporteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReporteServiceImpl implements ReporteService {

    private final PagoRepository pagoRepository;

    @Override
    public CajaDiariaResponse obtenerCajaDiaria(LocalDate fecha) {
        log.info("Generando reporte de caja diaria para fecha: {}", fecha);

        LocalDateTime start = fecha.atStartOfDay();
        LocalDateTime end = fecha.atTime(LocalTime.MAX);

        List<Pago> pagos = pagoRepository.findByEstadoAndCreatedAtBetween(EstadoPago.APROBADO, start, end);

        Map<MetodoPago, BigDecimal> totals = pagos.stream()
                .collect(Collectors.groupingBy(
                        Pago::getMetodo,
                        Collectors.reducing(BigDecimal.ZERO, Pago::getMonto, BigDecimal::add)
                ));

        List<CajaDiariaResponse.MetodoTotal> detalles = totals.entrySet().stream()
                .map(entry -> new CajaDiariaResponse.MetodoTotal(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());

        log.info("Reporte generado. Cantidad de métodos con movimientos: {}", detalles.size());
        return new CajaDiariaResponse(detalles);
    }
}
