package com.padel.service;

import com.padel.dto.request.ProductoRequest;
import com.padel.dto.response.ProductoResponse;

import java.util.List;

public interface ProductoService {
    ProductoResponse crear(ProductoRequest request);
    ProductoResponse actualizar(Long id, ProductoRequest request);
    List<ProductoResponse> listarActivos();
    void eliminar(Long id);
    ProductoResponse obtenerPorId(Long id);
}
