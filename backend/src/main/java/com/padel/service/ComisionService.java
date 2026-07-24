package com.padel.service;

import com.padel.domain.entity.Bono;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Usuario;
import com.padel.dto.response.ReporteComisionesResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ComisionService {
    void registrarComisionConsumo(Usuario recepcionista, Pago pago, BigDecimal montoBase);
    void registrarComisionBono(Usuario recepcionista, Bono bono);
    ReporteComisionesResponse reporte(Long recepcionistaId, LocalDate desde, LocalDate hasta);
}
