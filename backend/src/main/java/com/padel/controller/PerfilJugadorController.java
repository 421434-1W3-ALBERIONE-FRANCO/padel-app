package com.padel.controller;

import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.PerfilJugadorResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.service.PerfilJugadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/jugadores/me/perfil")
@RequiredArgsConstructor
@Tag(name = "Perfil de jugador", description = "Perfil deportivo del usuario autenticado: categoría, posición y disponibilidad semanal")
@SecurityRequirement(name = "Bearer Authentication")
public class PerfilJugadorController {

    private final PerfilJugadorService perfilJugadorService;

    @GetMapping
    @Operation(summary = "Obtener mi perfil de jugador", description = "Retorna el perfil deportivo del usuario autenticado.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
        @ApiResponse(responseCode = "404", description = "El usuario todavía no completó su perfil")
    })
    public ResponseEntity<PerfilJugadorResponse> obtener(Principal principal) {
        return ResponseEntity.ok(perfilJugadorService.obtenerMiPerfil(principal.getName()));
    }

    @PutMapping
    @Operation(summary = "Crear o actualizar mi perfil de jugador", description = "Guarda categoría, posición, presentación, visibilidad en el match y los bloques de la semana en los que puede jugar.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Perfil guardado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    public ResponseEntity<PerfilJugadorResponse> guardar(@Valid @RequestBody PerfilJugadorRequest request, Principal principal) {
        return ResponseEntity.ok(perfilJugadorService.guardarMiPerfil(request, principal.getName()));
    }

    /** Tope de la foto que se recibe (el front ya la manda recortada a 512px, unos 100 KB). */
    static final int TAMANO_MAXIMO_FOTO = 1_000_000;

    // La foto llega como cuerpo binario (Content-Type image/jpeg o image/png), no multipart: la API no recibe
    // formularios con archivos. Se lee con tope para no cargar en memoria un cuerpo arbitrariamente grande.
    @PutMapping(value = "/foto", consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    @Operation(summary = "Cargar o reemplazar mi foto de perfil",
            description = "Recibe la imagen como cuerpo binario (JPG o PNG, hasta 1 MB). Se recorta al cuadrado del centro, " +
                    "se lleva a 512x512 y se guarda como JPEG sin metadatos. Requiere tener el perfil de jugador guardado.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Foto guardada; el perfil vuelve con la nueva fotoVersion"),
        @ApiResponse(responseCode = "400", description = "No es una imagen válida, es demasiado chica o supera 1 MB"),
        @ApiResponse(responseCode = "409", description = "Todavía no guardó su perfil de jugador"),
        @ApiResponse(responseCode = "415", description = "Content-Type distinto de image/jpeg o image/png")
    })
    public ResponseEntity<PerfilJugadorResponse> guardarFoto(HttpServletRequest request, Principal principal) throws IOException {
        if (request.getContentLengthLong() > TAMANO_MAXIMO_FOTO) {
            throw new DatosInvalidosException("La foto no puede superar 1 MB");
        }
        byte[] imagen = request.getInputStream().readNBytes(TAMANO_MAXIMO_FOTO + 1);
        if (imagen.length > TAMANO_MAXIMO_FOTO) {
            throw new DatosInvalidosException("La foto no puede superar 1 MB");
        }
        return ResponseEntity.ok(perfilJugadorService.guardarMiFoto(imagen, principal.getName()));
    }

    @DeleteMapping("/foto")
    @Operation(summary = "Quitar mi foto de perfil", description = "Borra la foto; el perfil vuelve sin fotoVersion.")
    public ResponseEntity<PerfilJugadorResponse> eliminarFoto(Principal principal) {
        return ResponseEntity.ok(perfilJugadorService.eliminarMiFoto(principal.getName()));
    }
}
