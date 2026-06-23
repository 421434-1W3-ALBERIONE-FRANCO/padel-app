package com.padel.service.impl;

import com.padel.domain.entity.Producto;
import com.padel.dto.request.ProductoRequest;
import com.padel.dto.response.ProductoResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.ProductoMapper;
import com.padel.repository.ProductoRepository;
import com.padel.service.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;

    @Override
    public ProductoResponse crear(ProductoRequest request) {
        log.info("Creando nuevo producto: {}", request.nombre());
        Producto producto = productoMapper.toEntity(request);
        producto.setActivo(true);
        Producto saved = productoRepository.save(producto);
        return productoMapper.toResponse(saved);
    }

    @Override
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        log.info("Actualizando producto ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        productoMapper.updateFromRequest(request, producto);
        Producto saved = productoRepository.save(producto);
        return productoMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponse> listarActivos() {
        log.info("Listando productos activos");
        return productoRepository.findByActivoTrue().stream()
                .map(productoMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void eliminar(Long id) {
        log.info("Eliminación lógica de producto ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorId(Long id) {
        log.info("Obteniendo producto ID: {}", id);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        return productoMapper.toResponse(producto);
    }
}
