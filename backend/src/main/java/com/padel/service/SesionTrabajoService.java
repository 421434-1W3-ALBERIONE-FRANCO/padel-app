package com.padel.service;

import com.padel.dto.response.BancoHorasResponse;
import com.padel.dto.response.FichajeResponse;

public interface SesionTrabajoService {
    void cerrarSesionesHuerfanas();
    BancoHorasResponse consultarBancoHoras(Long empleadoId);

    /** Fichaje explícito de entrada (antes la jornada se abría sola con el login). */
    FichajeResponse ficharEntrada(String email, String ip);

    FichajeResponse ficharSalida(String email, String ip);

    FichajeResponse fichajeActual(String email);
}
