package com.padel.service;

import com.padel.dto.response.DisponibilidadResponse;

import java.time.LocalDate;

public interface DisponibilidadService {
    DisponibilidadResponse obtenerDisponibilidad(Long canchaId, LocalDate fecha);
}
