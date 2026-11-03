package com.padel.dto.response;

/** Los bytes de una foto de perfil para devolverla tal cual (no es JSON). */
public record FotoPerfilArchivo(byte[] contenido, String tipoContenido, Long version) {}
