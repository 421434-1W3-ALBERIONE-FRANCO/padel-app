package com.padel.controller;

import com.padel.dto.request.CobroMostradorRequest;
import com.padel.dto.request.ReservaRequest;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.ReservaResponse;
import com.padel.service.PagoService;
import com.padel.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Endpoints para la gestión de reservas de canchas de pádel")
@SecurityRequirement(name = "Bearer Authentication")
public class ReservaController {

    private final ReservaService reservaService;
    private final PagoService pagoService;

    @PostMapping
    @Operation(summary = "Crear una nueva reserva", description = "Registra una reserva para un usuario en estado PENDIENTE_PAGO. Valida concurrencia con lock Redis, bloqueos de cancha y reservas previas.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Reserva creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "409", description = "Slot no disponible (ya reservado o bloqueado)"),
        @ApiResponse(responseCode = "404", description = "Cancha, franja u usuario no encontrados")
    })
    public ResponseEntity<ReservaResponse> crear(
            @Valid @RequestBody ReservaRequest request,
            Principal principal) {
        ReservaResponse response = reservaService.crearReserva(request, principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar una reserva existente", description = "Cancela una reserva. Los usuarios sólo pueden cancelar sus propias reservas; los administradores y recepcionistas pueden cancelar cualquiera.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reserva cancelada exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "422", description = "La reserva no es modificable (ya completada o cancelada)"),
        @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    })
    public ResponseEntity<ReservaResponse> cancelar(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            Principal principal) {
        ReservaResponse response = reservaService.cancelarReserva(id, principal.getName(), motivo);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mis-reservas")
    @Operation(summary = "Obtener reservas del usuario logueado", description = "Retorna el historial de reservas asociadas al usuario autenticado actual.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<List<ReservaResponse>> obtenerMisReservas(Principal principal) {
        List<ReservaResponse> response = reservaService.obtenerMisReservas(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener reserva por ID", description = "Retorna los detalles de una reserva específica. Los usuarios sólo pueden ver sus propias reservas; los administradores y recepcionistas pueden ver cualquiera.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reserva encontrada y retornada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    })
    public ResponseEntity<ReservaResponse> obtenerPorId(
            @PathVariable Long id,
            Principal principal) {
        ReservaResponse response = reservaService.obtenerPorId(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(summary = "Obtener todas las reservas (Admin/Recepción)", description = "Sin parámetros retorna la lista completa de todas las reservas registradas en el sistema. " +
            "Con `fecha` (yyyy-MM-dd) retorna sólo las de ese día, en todos sus estados, ordenadas por hora de inicio y cancha. Sólo accesible por administradores y recepcionistas.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de reservas obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Fecha con formato inválido"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Permisos insuficientes")
    })
    public ResponseEntity<List<ReservaResponse>> obtenerTodas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        List<ReservaResponse> response = fecha != null
                ? reservaService.obtenerPorFecha(fecha)
                : reservaService.obtenerTodas();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(
        summary = "Confirmar una reserva sin registrar el cobro (Admin/Recepción)",
        description = "Confirma una reserva en estado PENDIENTE_PAGO sin pasar por el webhook de MercadoPago y sin " +
                      "registrar un pago: para un turno que se paga al llegar (queda pendiente de cobro en la caja " +
                      "del día hasta usar /cobro-mostrador). Para cobrar en el acto usar /cobro-mostrador."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reserva confirmada exitosamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
        @ApiResponse(responseCode = "404", description = "Reserva no encontrada"),
        @ApiResponse(responseCode = "422", description = "La reserva no es modificable (no está en PENDIENTE_PAGO)")
    })
    public ResponseEntity<ReservaResponse> confirmar(@PathVariable Long id) {
        ReservaResponse response = reservaService.confirmarReserva(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cobro-mostrador")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    @Operation(
        summary = "Cobrar un turno en el mostrador (Admin/Recepción)",
        description = "Registra un pago APROBADO en efectivo o con tarjeta por el precio del turno, atendido por quien lo " +
                      "registra, y confirma la reserva si estaba PENDIENTE_PAGO. Sirve también para cobrar un turno " +
                      "confirmado para pagar al llegar. Aparece en la caja diaria."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Cobro registrado"),
        @ApiResponse(responseCode = "400", description = "Método inválido: en el mostrador es EFECTIVO o TARJETA"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Permisos insuficientes"),
        @ApiResponse(responseCode = "404", description = "Reserva no encontrada"),
        @ApiResponse(responseCode = "409", description = "El turno ya tiene un cobro registrado"),
        @ApiResponse(responseCode = "422", description = "La reserva está cancelada o no admite cobro")
    })
    public ResponseEntity<PagoResponse> cobrarEnMostrador(@PathVariable Long id,
                                                          @Valid @RequestBody CobroMostradorRequest request,
                                                          Principal principal) {
        PagoResponse response = pagoService.registrarCobroMostrador(id, request.metodo(), principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
