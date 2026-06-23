package com.padel.controller;

import com.padel.dto.request.ProductoRequest;
import com.padel.dto.response.ProductoResponse;
import com.padel.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Endpoints para la gestión del catálogo de productos (tienda/cantina)")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductoController {

    private final ProductoService productoService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un nuevo producto", description = "Registra un producto en el catálogo. Sólo accesible por administradores.")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        ProductoResponse response = productoService.crear(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Listar productos activos", description = "Retorna la lista de productos activos en el catálogo. Accesible por administradores y recepcionistas.")
    public ResponseEntity<List<ProductoResponse>> listarActivos() {
        List<ProductoResponse> response = productoService.listarActivos();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar un producto", description = "Modifica los datos y stock de un producto específico. Sólo accesible por administradores.")
    public ResponseEntity<ProductoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        ProductoResponse response = productoService.actualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminación lógica de un producto", description = "Desactiva un producto en el catálogo (activo = false). Sólo accesible por administradores.")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
