package com.padel.service;

import com.padel.domain.entity.Usuario;
import com.padel.dto.response.BancoHorasResponse;

public interface SesionTrabajoService {
    void abrirSesion(Usuario empleado);
    void cerrarSesion(Usuario empleado, boolean automatica);
    void cerrarSesionesHuerfanas();
    BancoHorasResponse consultarBancoHoras(Long empleadoId);
}
