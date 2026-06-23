package com.padel.service;

import com.padel.dto.response.CajaDiariaResponse;
import java.time.LocalDate;

public interface ReporteService {
    CajaDiariaResponse obtenerCajaDiaria(LocalDate fecha);
}
