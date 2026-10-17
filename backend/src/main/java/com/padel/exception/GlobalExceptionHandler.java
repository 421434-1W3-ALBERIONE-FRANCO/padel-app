package com.padel.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EmailDuplicadoException.class)
    public ResponseEntity<ApiError> handleEmailDuplicadoException(EmailDuplicadoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NombreDuplicadoException.class)
    public ResponseEntity<ApiError> handleNombreDuplicadoException(NombreDuplicadoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SolapamientoHorarioException.class)
    public ResponseEntity<ApiError> handleSolapamientoHorarioException(SolapamientoHorarioException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SolapamientoBloqueoException.class)
    public ResponseEntity<ApiError> handleSolapamientoBloqueoException(SolapamientoBloqueoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SlotNoDisponibleException.class)
    public ResponseEntity<ApiError> handleSlotNoDisponibleException(SlotNoDisponibleException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BonoNoDisponibleException.class)
    public ResponseEntity<ApiError> handleBonoNoDisponibleException(BonoNoDisponibleException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<ApiError> handleStockInsuficienteException(StockInsuficienteException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ReservaNoModificableException.class)
    public ResponseEntity<ApiError> handleReservaNoModificableException(ReservaNoModificableException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(RangoHorarioInvalidoException.class)
    public ResponseEntity<ApiError> handleRangoHorarioInvalidoException(RangoHorarioInvalidoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ApiError> handleEstadoInvalidoException(EstadoInvalidoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CupoCompletoException.class)
    public ResponseEntity<ApiError> handleCupoCompletoException(CupoCompletoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ApiError> handleDatosInvalidosException(DatosInvalidosException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PasswordIncorrectoException.class)
    public ResponseEntity<ApiError> handlePasswordIncorrectoException(PasswordIncorrectoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ApiError> handleTokenInvalidoException(TokenInvalidoException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Error de validación: " + validationErrors,
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Cubre, entre otros casos, fechas con un año fuera de lo razonable escritas a mano
        // (ej. "20260-01-01" con 5 dígitos): Jackson no logra parsear el LocalDate/LocalDateTime
        // y lanza esta excepción antes de que el DTO llegue a las validaciones de Bean Validation.
        log.warn("Cuerpo de la solicitud inválido en {}: {}", request.getRequestURI(), ex.getMessage());
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "El cuerpo de la solicitud tiene un formato inválido. Revise que las fechas y números tengan un formato correcto (por ejemplo, el año no puede tener más de 4 dígitos).",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Credenciales incorrectas",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    // Login de una cuenta desactivada (DisabledException) u otro fallo de autenticación distinto de
    // credenciales incorrectas: antes caía en el handler genérico y respondía 500.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String mensaje = ex instanceof DisabledException
                ? "La cuenta está desactivada. Contactá al club."
                : "No se pudo autenticar al usuario";
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                mensaje,
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "No tiene permisos para acceder a este recurso",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // Parámetro de path/query con formato inválido (ej. /rankings/NOVENA, ?fecha=17-10-2026, ?cancha=abc).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "El valor '" + ex.getValue() + "' no es válido para el parámetro '" + ex.getName() + "'",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Violación de una restricción de la base (unicidad, FK): es un conflicto de datos, no un error interno.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violación de integridad de datos en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "La operación entra en conflicto con datos existentes (registro duplicado o en uso).",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        // Excepciones propias de Spring MVC que ya traen su status (ruta inexistente -> 404, método no
        // soportado -> 405, falta un parámetro obligatorio -> 400, content-type inválido -> 415...).
        // Sin esto el catch-all las convertía en 500.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_REQUEST;
            }
            ApiError error = new ApiError(
                    LocalDateTime.now(),
                    status.value(),
                    status.getReasonPhrase(),
                    mensajeParaErrorDeFramework(status),
                    request.getRequestURI()
            );
            return new ResponseEntity<>(error, status);
        }

        // No exponer ex.getMessage() al cliente: puede filtrar detalles internos (SQL, stacktraces,
        // configuración de terceros). El detalle completo queda solo en el log del servidor.
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Ocurrió un error interno. Por favor intente nuevamente.",
                request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String mensajeParaErrorDeFramework(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe";
            case METHOD_NOT_ALLOWED -> "Método HTTP no soportado para este recurso";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
            case NOT_ACCEPTABLE -> "Formato de respuesta no soportado";
            default -> "La solicitud es inválida o está incompleta";
        };
    }
}
