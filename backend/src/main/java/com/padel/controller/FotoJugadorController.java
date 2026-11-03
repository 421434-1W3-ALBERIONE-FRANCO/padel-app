package com.padel.controller;

import com.padel.dto.response.FotoPerfilArchivo;
import com.padel.service.PerfilJugadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/jugadores")
@RequiredArgsConstructor
@Tag(name = "Perfil de jugador", description = "Perfil deportivo del usuario autenticado: categoría, posición y disponibilidad semanal")
@SecurityRequirement(name = "Bearer Authentication")
public class FotoJugadorController {

    private final PerfilJugadorService perfilJugadorService;

    @GetMapping(value = "/{usuarioId}/foto", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Ver la foto de perfil de un jugador",
            description = "La ven el propio jugador, el staff, quien lo puede encontrar en el mazo del match y sus matches. " +
                    "El front agrega ?v={fotoVersion}: cada versión es una URL distinta y se puede cachear.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "La imagen (JPEG 512x512)"),
        @ApiResponse(responseCode = "404", description = "No tiene foto o quien consulta no puede verla")
    })
    public ResponseEntity<byte[]> obtener(@PathVariable Long usuarioId, Principal principal) {
        FotoPerfilArchivo foto = perfilJugadorService.obtenerFoto(usuarioId, principal.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.tipoContenido()))
                // private: es una respuesta autenticada, no la guarda un proxy compartido.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .eTag("\"" + foto.version() + "\"")
                .body(foto.contenido());
    }
}
