package com.padel.controller;

import com.padel.dto.request.TipoBonoRequest;
import com.padel.dto.response.TipoBonoResponse;
import com.padel.service.TipoBonoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tipos-bono")
@RequiredArgsConstructor
@Tag(name = "Tipos de bono", description = "Catálogo de bonos de horas que vende el club (horas, precio y vigencia)")
@SecurityRequirement(name = "Bearer Authentication")
public class TipoBonoController {

    private final TipoBonoService tipoBonoService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar el catálogo de bonos", description = "Los tipos activos: el staff los vende y los jugadores ven qué pueden comprar en recepción. Solo el admin puede pedir también los dados de baja.")
    public ResponseEntity<List<TipoBonoResponse>> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos,
                                                         Authentication authentication) {
        boolean esAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(tipoBonoService.listar(incluirInactivos && esAdmin));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un tipo de bono (Admin)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Tipo de bono creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o nombre repetido")
    })
    public ResponseEntity<TipoBonoResponse> crear(@Valid @RequestBody TipoBonoRequest request) {
        return new ResponseEntity<>(tipoBonoService.crear(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar un tipo de bono (Admin)", description = "Los bonos ya vendidos conservan las horas, el precio y el vencimiento con que se vendieron.")
    public ResponseEntity<TipoBonoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody TipoBonoRequest request) {
        return ResponseEntity.ok(tipoBonoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dar de baja un tipo de bono (Admin)", description = "Baja lógica: deja de ofrecerse para la venta.")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        tipoBonoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
