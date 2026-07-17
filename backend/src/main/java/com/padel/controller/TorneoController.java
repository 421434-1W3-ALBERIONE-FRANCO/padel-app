package com.padel.controller;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.TipoTorneo;
import com.padel.dto.request.InscripcionTorneoRequest;
import com.padel.dto.request.ResultadoPartidoRequest;
import com.padel.dto.request.TorneoRequest;
import com.padel.dto.response.InscripcionTorneoResponse;
import com.padel.dto.response.PartidoTorneoResponse;
import com.padel.dto.response.TorneoResponse;
import com.padel.service.TorneoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/torneos")
@RequiredArgsConstructor
@Tag(name = "Torneos", description = "Endpoints para la gestión de torneos, ligas y torneos express de pádel")
@SecurityRequirement(name = "Bearer Authentication")
public class TorneoController {

    private final TorneoService torneoService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un torneo o liga", description = "Registra un nuevo torneo, liga o torneo express. Sólo administradores.")
    public ResponseEntity<TorneoResponse> crear(@Valid @RequestBody TorneoRequest request) {
        TorneoResponse response = torneoService.crear(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Buscar torneos", description = "Lista torneos filtrando opcionalmente por estado, categoría y tipo.")
    public ResponseEntity<List<TorneoResponse>> buscar(
            @RequestParam(required = false) EstadoTorneo estado,
            @RequestParam(required = false) CategoriaJugador categoria,
            @RequestParam(required = false) TipoTorneo tipo) {
        return ResponseEntity.ok(torneoService.buscar(estado, categoria, tipo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener torneo por ID", description = "Retorna el detalle de un torneo específico.")
    public ResponseEntity<TorneoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(torneoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar un torneo", description = "Modifica los datos de un torneo mientras la inscripción está abierta. Sólo administradores.")
    public ResponseEntity<TorneoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody TorneoRequest request) {
        return ResponseEntity.ok(torneoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancelar un torneo", description = "Cancela un torneo que no haya finalizado. Sólo administradores.")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        torneoService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/inscripciones")
    @Operation(summary = "Inscribir una pareja", description = "Inscribe al usuario autenticado junto a un compañero en el torneo.")
    public ResponseEntity<InscripcionTorneoResponse> inscribirPareja(
            @PathVariable Long id,
            @Valid @RequestBody InscripcionTorneoRequest request,
            Principal principal) {
        InscripcionTorneoResponse response = torneoService.inscribirPareja(id, request, principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/inscripciones")
    @Operation(summary = "Listar inscripciones del torneo", description = "Retorna las parejas inscriptas en el torneo.")
    public ResponseEntity<List<InscripcionTorneoResponse>> listarInscripciones(@PathVariable Long id) {
        return ResponseEntity.ok(torneoService.listarInscripciones(id));
    }

    @DeleteMapping("/{id}/inscripciones/{inscripcionId}")
    @Operation(summary = "Cancelar una inscripción", description = "Cancela la inscripción de una pareja. Sólo los propios jugadores o un administrador.")
    public ResponseEntity<Void> cancelarInscripcion(
            @PathVariable Long id,
            @PathVariable Long inscripcionId,
            Principal principal) {
        torneoService.cancelarInscripcion(id, inscripcionId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fixture")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generar el fixture del torneo", description = "Genera los partidos de la primera ronda (o de la liga completa) según el formato del torneo. Sólo administradores.")
    public ResponseEntity<List<PartidoTorneoResponse>> generarFixture(@PathVariable Long id) {
        return new ResponseEntity<>(torneoService.generarFixture(id), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/partidos")
    @Operation(summary = "Listar partidos del torneo", description = "Retorna todos los partidos generados para el torneo.")
    public ResponseEntity<List<PartidoTorneoResponse>> listarPartidos(@PathVariable Long id) {
        return ResponseEntity.ok(torneoService.listarPartidos(id));
    }

    @PutMapping("/{id}/partidos/{partidoId}/resultado")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Cargar el resultado de un partido", description = "Carga el resultado en sets y actualiza el ranking de los jugadores. Administradores y recepcionistas.")
    public ResponseEntity<PartidoTorneoResponse> cargarResultado(
            @PathVariable Long id,
            @PathVariable Long partidoId,
            @Valid @RequestBody ResultadoPartidoRequest request) {
        return ResponseEntity.ok(torneoService.cargarResultado(id, partidoId, request));
    }

    @PostMapping("/{id}/siguiente-ronda")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generar la siguiente ronda", description = "Avanza a los ganadores de la ronda actual a la siguiente ronda en torneos de eliminación directa. Sólo administradores.")
    public ResponseEntity<List<PartidoTorneoResponse>> generarSiguienteRonda(@PathVariable Long id) {
        return new ResponseEntity<>(torneoService.generarSiguienteRonda(id), HttpStatus.CREATED);
    }
}
