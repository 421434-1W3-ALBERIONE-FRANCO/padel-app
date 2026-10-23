package com.padel.service;

import com.padel.dto.response.CajaDiariaResponse;
import com.padel.dto.response.MetricasReservasResponse;

import java.time.LocalDate;

public interface ReporteService {
    CajaDiariaResponse obtenerCajaDiaria(LocalDate fecha);

    MetricasReservasResponse metricasReservas(LocalDate desde, LocalDate hasta);
}
