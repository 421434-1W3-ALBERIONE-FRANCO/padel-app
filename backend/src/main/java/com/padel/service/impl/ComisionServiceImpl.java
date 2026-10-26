package com.padel.service.impl;

import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Comision;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoComision;
import com.padel.domain.enums.OrigenComision;
import com.padel.dto.response.ComisionResponse;
import com.padel.dto.response.ReporteComisionesResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.ComisionMapper;
import com.padel.repository.ComisionRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.ComisionService;
import com.padel.service.ConfiguracionSistemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComisionServiceImpl implements ComisionService {

    private final ComisionRepository comisionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final ComisionMapper comisionMapper;

    @Override
    @Transactional
    public void registrarComisionConsumo(Usuario recepcionista, Pago pago, BigDecimal montoBase) {
        registrar(recepcionista, OrigenComision.CONSUMO, pago, null, montoBase);
    }

    @Override
    @Transactional
    public void registrarComisionBono(Usuario recepcionista, Bono bono) {
        registrar(recepcionista, OrigenComision.BONO, null, bono, bono.getPrecioPagado());
    }

    private void registrar(Usuario recepcionista, OrigenComision origen, Pago pago, Bono bono, BigDecimal montoBase) {
        BigDecimal porcentaje = configuracionSistemaService.obtenerEntidad().getPorcentajeComisionRecepcion();
        BigDecimal montoComision = montoBase.multiply(porcentaje)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Comision comision = Comision.builder()
                .recepcionista(recepcionista)
                .origen(origen)
                .pago(pago)
                .bono(bono)
                .montoBase(montoBase)
                .porcentajeAplicado(porcentaje)
                .montoComision(montoComision)
                .estado(EstadoComision.PENDIENTE)
                .build();

        comisionRepository.save(comision);
        log.info("Comisión {} de ${} registrada para el recepcionista ID {}", origen, montoComision, recepcionista.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ReporteComisionesResponse reporte(Long recepcionistaId, LocalDate desde, LocalDate hasta) {
        Usuario recepcionista = usuarioRepository.findById(recepcionistaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + recepcionistaId));

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Comision> comisiones = comisionRepository.findByRecepcionistaIdAndCreatedAtBetween(recepcionistaId, inicio, fin);

        BigDecimal total = comisiones.stream()
                .map(Comision::getMontoComision)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ComisionResponse> detalle = comisiones.stream()
                .map(comisionMapper::toResponse)
                .toList();

        return new ReporteComisionesResponse(recepcionistaId, recepcionista.getEmail(), total, detalle);
    }
}
