package com.padel.service.impl;

import com.padel.domain.entity.ConfiguracionSistema;
import com.padel.dto.request.ConfiguracionRequest;
import com.padel.dto.response.ConfiguracionResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.ConfiguracionSistemaMapper;
import com.padel.repository.ConfiguracionSistemaRepository;
import com.padel.service.ConfiguracionSistemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfiguracionSistemaServiceImpl implements ConfiguracionSistemaService {

    private static final Long CONFIG_ID = 1L;

    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final ConfiguracionSistemaMapper configuracionSistemaMapper;

    @Override
    @Transactional(readOnly = true)
    public ConfiguracionResponse obtener() {
        return configuracionSistemaMapper.toResponse(obtenerEntidad());
    }

    @Override
    @Transactional
    public ConfiguracionResponse actualizar(ConfiguracionRequest request) {
        ConfiguracionSistema configuracion = obtenerEntidad();
        configuracion.setComisionesHabilitadas(request.comisionesHabilitadas());
        configuracion.setPorcentajeComisionRecepcion(request.porcentajeComisionRecepcion());
        configuracion.setDiasAvisoVencimientoBono(request.diasAvisoVencimientoBono());
        configuracion.setHoraTopeCierreSesion(request.horaTopeCierreSesion());
        ConfiguracionSistema guardada = configuracionSistemaRepository.save(configuracion);
        return configuracionSistemaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public ConfiguracionSistema obtenerEntidad() {
        return configuracionSistemaRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new ResourceNotFoundException("La configuración del sistema no está inicializada."));
    }
}
