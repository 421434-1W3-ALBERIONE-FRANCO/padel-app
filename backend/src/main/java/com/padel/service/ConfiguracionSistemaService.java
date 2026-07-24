package com.padel.service;

import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.dto.request.ConfiguracionRequest;
import com.padel.dto.response.ConfiguracionResponse;

public interface ConfiguracionSistemaService {
    ConfiguracionResponse obtener();
    ConfiguracionResponse actualizar(ConfiguracionRequest request);

    /**
     * Acceso directo a la entidad para otros services (schedulers, comisiones, control horario)
     * que necesitan los valores crudos sin pasar por el mapeo a DTO.
     */
    ConfiguracionSistema obtenerEntidad();
}
