package com.padel.controller;

import com.padel.dto.request.ReservaMatchRequest;
import com.padel.dto.request.SwipeRequest;
import com.padel.dto.response.CandidatoMatchResponse;
import com.padel.dto.response.MatchParejaResponse;
import com.padel.dto.response.ReservaResponse;
import com.padel.dto.response.SwipeResponse;
import com.padel.dto.response.TurnoSugeridoResponse;
import com.padel.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/match")
@RequiredArgsConstructor
@Tag(name = "Match de parejas", description = "Buscar pareja deslizando tarjetas de jugadores compatibles por categoría y disponibilidad")
@SecurityRequirement(name = "Bearer Authentication")
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/candidatos")
    @Operation(summary = "Mazo de candidatos", description = "Jugadores visibles de categoría cercana (±1) con al menos un horario en común, que todavía no respondiste, ordenados por afinidad.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Mazo obtenido"),
        @ApiResponse(responseCode = "409", description = "El usuario no completó su perfil o lo tiene oculto")
    })
    public ResponseEntity<List<CandidatoMatchResponse>> candidatos(
            @RequestParam(defaultValue = "20") int limite,
            Principal principal) {
        return ResponseEntity.ok(matchService.obtenerCandidatos(principal.getName(), limite));
    }

    @PostMapping("/swipes")
    @Operation(summary = "Responder una tarjeta", description = "Registra \"me interesa\" (LIKE) o \"paso\" (PASS). Si el otro jugador también te eligió, se crea el match y se libera el contacto.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Respuesta registrada (indica si hubo match)"),
        @ApiResponse(responseCode = "404", description = "El jugador no está disponible en el match"),
        @ApiResponse(responseCode = "409", description = "Ya respondiste a este jugador o tu perfil no está listo")
    })
    public ResponseEntity<SwipeResponse> swipe(@Valid @RequestBody SwipeRequest request, Principal principal) {
        return new ResponseEntity<>(matchService.registrarSwipe(request, principal.getName()), HttpStatus.CREATED);
    }

    @GetMapping("/parejas")
    @Operation(summary = "Mis matches", description = "Matches mutuos del usuario con el contacto del compañero y los horarios que comparten.")
    public ResponseEntity<List<MatchParejaResponse>> parejas(Principal principal) {
        return ResponseEntity.ok(matchService.obtenerMisParejas(principal.getName()));
    }

    @GetMapping("/parejas/{id}/turnos-sugeridos")
    @Operation(summary = "Turnos para jugar juntos", description = "Turnos libres de las próximas fechas en los horarios que ambos tienen disponibles.")
    public ResponseEntity<List<TurnoSugeridoResponse>> turnosSugeridos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") int dias,
            Principal principal) {
        return ResponseEntity.ok(matchService.sugerirTurnos(id, principal.getName(), dias));
    }

    @PostMapping("/parejas/{id}/reservas")
    @Operation(summary = "Reservar juntos", description = "Crea la reserva (pendiente de pago) a nombre de quien la hace y le avisa al compañero del match.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Reserva creada"),
        @ApiResponse(responseCode = "404", description = "Match no encontrado"),
        @ApiResponse(responseCode = "409", description = "El turno ya no está disponible")
    })
    public ResponseEntity<ReservaResponse> reservarJuntos(
            @PathVariable Long id,
            @Valid @RequestBody ReservaMatchRequest request,
            Principal principal) {
        return new ResponseEntity<>(matchService.reservarJuntos(id, request, principal.getName()), HttpStatus.CREATED);
    }
}
