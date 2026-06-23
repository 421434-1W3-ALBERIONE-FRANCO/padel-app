package com.padel.service.impl;

import com.padel.domain.entity.Producto;
import com.padel.dto.request.ProductoRequest;
import com.padel.dto.response.ProductoResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.ProductoMapper;
import com.padel.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ProductoMapper productoMapper;

    @InjectMocks
    private ProductoServiceImpl productoService;

    private Producto productoMock;
    private ProductoRequest productoRequest;
    private ProductoResponse productoResponse;

    @BeforeEach
    void setUp() {
        productoMock = Producto.builder()
                .id(1L)
                .nombre("Coca Cola")
                .categoria("BEBIDA")
                .precio(new BigDecimal("150.00"))
                .stock(10)
                .activo(true)
                .build();

        productoRequest = new ProductoRequest(
                "Coca Cola",
                "BEBIDA",
                new BigDecimal("150.00"),
                10,
                true
        );

        productoResponse = new ProductoResponse(
                1L,
                "Coca Cola",
                "BEBIDA",
                new BigDecimal("150.00"),
                10,
                true
        );
    }

    @Test
    void crear_DeberiaGuardarYRetornarProducto() {
        when(productoMapper.toEntity(any(ProductoRequest.class))).thenReturn(productoMock);
        when(productoRepository.save(any(Producto.class))).thenReturn(productoMock);
        when(productoMapper.toResponse(any(Producto.class))).thenReturn(productoResponse);

        ProductoResponse result = productoService.crear(productoRequest);

        assertNotNull(result);
        assertEquals("Coca Cola", result.nombre());
        verify(productoRepository, times(1)).save(any(Producto.class));
    }

    @Test
    void actualizar_DeberiaModificarYRetornarProducto() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoMock));
        doAnswer(invocation -> {
            Producto p = invocation.getArgument(1);
            p.setNombre("Coca Cola Zero");
            return null;
        }).when(productoMapper).updateFromRequest(any(ProductoRequest.class), any(Producto.class));

        Producto updatedMock = Producto.builder()
                .id(1L)
                .nombre("Coca Cola Zero")
                .categoria("BEBIDA")
                .precio(new BigDecimal("150.00"))
                .stock(10)
                .activo(true)
                .build();

        when(productoRepository.save(any(Producto.class))).thenReturn(updatedMock);
        when(productoMapper.toResponse(any(Producto.class))).thenReturn(
                new ProductoResponse(1L, "Coca Cola Zero", "BEBIDA", new BigDecimal("150.00"), 10, true)
        );

        ProductoResponse result = productoService.actualizar(1L, productoRequest);

        assertNotNull(result);
        assertEquals("Coca Cola Zero", result.nombre());
        verify(productoRepository, times(1)).findById(1L);
        verify(productoRepository, times(1)).save(any(Producto.class));
    }

    @Test
    void actualizar_DeberiaLanzarExcepcionSiNoExiste() {
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productoService.actualizar(1L, productoRequest));
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void listarActivos_DeberiaRetornarListaDeProductos() {
        when(productoRepository.findByActivoTrue()).thenReturn(Collections.singletonList(productoMock));
        when(productoMapper.toResponse(any(Producto.class))).thenReturn(productoResponse);

        List<ProductoResponse> result = productoService.listarActivos();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        verify(productoRepository, times(1)).findByActivoTrue();
    }

    @Test
    void eliminar_DeberiaRealizarBajaLogica() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoMock));
        when(productoRepository.save(any(Producto.class))).thenReturn(productoMock);

        productoService.eliminar(1L);

        assertFalse(productoMock.isActivo());
        verify(productoRepository, times(1)).findById(1L);
        verify(productoRepository, times(1)).save(productoMock);
    }

    @Test
    void obtenerPorId_DeberiaRetornarProducto() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoMock));
        when(productoMapper.toResponse(any(Producto.class))).thenReturn(productoResponse);

        ProductoResponse result = productoService.obtenerPorId(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(productoRepository, times(1)).findById(1L);
    }
}
