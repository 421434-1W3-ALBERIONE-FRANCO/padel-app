package com.padel.util;

import com.padel.exception.DatosInvalidosException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class FechaHoraUtil {

    private static final int MAX_ANIOS_A_FUTURO = 5;

    /**
     * Guarda contra años disparatados (ej. un typo que carga 20260 en vez de 2026):
     * el año debe estar entre el actual y el actual + MAX_ANIOS_A_FUTURO. Se aplica a
     * cualquier fecha de inicio/fin de torneos, bloqueos, bonos y solicitudes de partido.
     */
    public void validarAnioRazonable(LocalDate fecha, String campo) {
        if (fecha == null) {
            return;
        }
        int anioActual = LocalDate.now().getYear();
        int anioMaximo = anioActual + MAX_ANIOS_A_FUTURO;
        if (fecha.getYear() < anioActual || fecha.getYear() > anioMaximo) {
            throw new DatosInvalidosException(
                    "El año de " + campo + " no es válido: debe estar entre " + anioActual + " y " + anioMaximo);
        }
    }
}
