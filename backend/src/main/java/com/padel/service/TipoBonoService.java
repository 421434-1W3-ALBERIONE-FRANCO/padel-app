package com.padel.service;

import com.padel.dto.request.TipoBonoRequest;
import com.padel.dto.response.TipoBonoResponse;

import java.util.List;

public interface TipoBonoService {

    /** Activos para vender; con {@code incluirInactivos} también los dados de baja (pantalla del admin). */
    List<TipoBonoResponse> listar(boolean incluirInactivos);

    TipoBonoResponse crear(TipoBonoRequest request);

    TipoBonoResponse actualizar(Long id, TipoBonoRequest request);

    /** Baja lógica: los bonos ya vendidos de este tipo no cambian. */
    void desactivar(Long id);
}
