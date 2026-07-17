package com.padel.controller;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoSolicitud;
import com.padel.domain.enums.TipoSolicitud;
import com.padel.dto.request.PostulacionSolicitudRequest;
import com.padel.dto.request.SolicitudPartidoRequest;
import com.padel.dto.response.SolicitudPartidoResponse;
import com.padel.service.SolicitudPartidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/solicitudes-partido")
@RequiredArgsConstructor
@Tag(name = "Solicitudes de Partido", description = "Tablón para buscar pareja o jugadores y completar partidos con desconocidos")
@SecurityRequirement(name = "Bearer Authentication")
public class SolicitudPartidoController {

    private final SolicitudPartidoService solicitudPartidoService;

    @PostMapping
    @Operation(summary = "Crear una solicitud de partido", description = "Publica una búsqueda de pareja o de jugadores para completar una cancha.")
    public ResponseEntity<SolicitudPartidoResponse> crear(
            @Valid @RequestBody SolicitudPartidoRequest request,
            Principal principal) {
        SolicitudPartidoResponse response = solicitudPartidoService.crear(request, principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Buscar solicitudes de partido", description = "Lista el tablón de solicitudes, filtrando opcionalmente por tipo, categoría y estado (por defecto ABIERTA).")
    public ResponseEntity<List<SolicitudPartidoResponse>> buscar(
            @RequestParam(required = false) TipoSolicitud tipoSolicitud,
            @RequestParam(required = false) CategoriaJugador categoria,
            @RequestParam(required = false) EstadoSolicitud estado) {
        return ResponseEntity.ok(solicitudPartidoService.buscar(tipoSolicitud, categoria, estado));
    }

    @GetMapping("/mis-solicitudes")
    @Operation(summary = "Mis solicitudes creadas", description = "Lista las solicitudes de partido creadas por el usuario autenticado.")
    public ResponseEntity<List<SolicitudPartidoResponse>> misSolicitudes(Principal principal) {
        return ResponseEntity.ok(solicitudPartidoService.misSolicitudes(principal.getName()));
    }

    @GetMapping("/mis-postulaciones")
    @Operation(summary = "Mis postulaciones", description = "Lista las solicitudes de partido a las que se postuló el usuario autenticado.")
    public ResponseEntity<List<SolicitudPartidoResponse>> misPostulaciones(Principal principal) {
        return ResponseEntity.ok(solicitudPartidoService.misPostulaciones(principal.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener solicitud por ID", description = "Retorna el detalle de una solicitud junto a sus postulaciones.")
    public ResponseEntity<SolicitudPartidoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(solicitudPartidoService.obtenerPorId(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar una solicitud", description = "Cancela una solicitud de partido. Sólo el creador puede cancelarla.")
    public ResponseEntity<Void> cancelar(@PathVariable Long id, Principal principal) {
        solicitudPartidoService.cancelar(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/postulaciones")
    @Operation(summary = "Postularse a una solicitud", description = "El usuario autenticado se postula para completar la solicitud de partido.")
    public ResponseEntity<SolicitudPartidoResponse> postularse(
            @PathVariable Long id,
            @Valid @RequestBody PostulacionSolicitudRequest request,
            Principal principal) {
        SolicitudPartidoResponse response = solicitudPartidoService.postularse(id, request, principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/postulaciones/{postulacionId}/aceptar")
    @Operation(summary = "Aceptar una postulación", description = "El creador de la solicitud acepta a un jugador postulado.")
    public ResponseEntity<SolicitudPartidoResponse> aceptarPostulacion(
            @PathVariable Long id,
            @PathVariable Long postulacionId,
            Principal principal) {
        return ResponseEntity.ok(solicitudPartidoService.aceptarPostulacion(id, postulacionId, principal.getName()));
    }

    @PutMapping("/{id}/postulaciones/{postulacionId}/rechazar")
    @Operation(summary = "Rechazar una postulación", description = "El creador de la solicitud rechaza a un jugador postulado.")
    public ResponseEntity<SolicitudPartidoResponse> rechazarPostulacion(
            @PathVariable Long id,
            @PathVariable Long postulacionId,
            Principal principal) {
        return ResponseEntity.ok(solicitudPartidoService.rechazarPostulacion(id, postulacionId, principal.getName()));
    }
}
