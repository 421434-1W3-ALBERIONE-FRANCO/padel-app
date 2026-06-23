package com.padel.controller;

import com.padel.dto.request.PreferenciaRequest;
import com.padel.dto.response.PagoResponse;
import com.padel.dto.response.PreferenciaResponse;
import com.padel.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Endpoints para la gestión de pagos y pasarela de MercadoPago")
@Slf4j
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/preferencias")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Generar preferencia de MercadoPago", description = "Genera un preferenceId y link de checkout para abonar una reserva del usuario.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Preferencia generada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Reserva o usuario no encontrado")
    })
    public ResponseEntity<PreferenciaResponse> crearPreferencia(
            @Valid @RequestBody PreferenciaRequest request,
            Principal principal) {
        PreferenciaResponse response = pagoService.crearPreferencia(request.reservaId(), principal.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/webhook/mercadopago")
    @Operation(summary = "Webhook de MercadoPago", description = "Recibe notificaciones automáticas de cambio de estado de pagos desde MercadoPago de forma pública.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notificación procesada correctamente")
    })
    public ResponseEntity<Void> recibirWebhook(
            @RequestParam(value = "type", required = false) String queryType,
            @RequestParam(value = "id", required = false) String queryId,
            @RequestParam(value = "topic", required = false) String queryTopic,
            @RequestParam(value = "data.id", required = false) String queryDataId,
            @RequestBody(required = false) Map<String, Object> body) {
        
        log.info("Webhook recibido. Query type: {}, Query id: {}, Query topic: {}, Query data.id: {}, Body: {}", 
                queryType, queryId, queryTopic, queryDataId, body);

        String type = queryType != null ? queryType : (queryTopic != null ? queryTopic : null);
        String paymentId = queryDataId != null ? queryDataId : (queryId != null ? queryId : null);

        if (body != null) {
            if (type == null && body.containsKey("type")) {
                type = String.valueOf(body.get("type"));
            }
            if (paymentId == null && body.containsKey("data") && body.get("data") instanceof Map) {
                Map<?, ?> dataMap = (Map<?, ?>) body.get("data");
                if (dataMap.containsKey("id")) {
                    paymentId = String.valueOf(dataMap.get("id"));
                }
            }
        }

        if (type != null && paymentId != null) {
            pagoService.procesarWebhook(type, paymentId);
        } else {
            log.warn("Webhook omitido por falta de parámetros requeridos (type/paymentId)");
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Obtener detalle de pago por ID", description = "Retorna los detalles de un pago. Los usuarios sólo pueden ver sus propios pagos; administradores y recepcionistas pueden ver cualquiera.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pago encontrado"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Pago o usuario no encontrado")
    })
    public ResponseEntity<PagoResponse> obtenerPorId(
            @PathVariable Long id,
            Principal principal) {
        PagoResponse response = pagoService.obtenerPorId(id, principal.getName());
        return ResponseEntity.ok(response);
    }
}
