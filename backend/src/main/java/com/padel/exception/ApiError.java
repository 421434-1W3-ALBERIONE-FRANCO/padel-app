package com.padel.exception;

import java.time.LocalDateTime;

/**
 * Formato único de error de la API. {@code codigo} es opcional (se omite del JSON cuando es null) y solo
 * aparece en los errores que el frontend necesita distinguir.
 */
public record ApiError(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    String codigo
) {
    public ApiError(LocalDateTime timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}
