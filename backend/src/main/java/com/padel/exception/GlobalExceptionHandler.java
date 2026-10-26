package com.padel.exception;

import com.padel.integracion.pagos.PasarelaPagosException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Traduce excepciones a respuestas {@link ApiError}. Las de negocio declaran su propio status
 * ({@link NegocioException}), así que un solo handler reemplaza a los quince casi idénticos de antes.
 * Nunca se expone el mensaje de una excepción inesperada: puede filtrar SQL, rutas o configuración.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ApiError> handleNegocio(NegocioException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(ex.getStatus());
        if (ex instanceof DemasiadosIntentosException limite) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(limite.getSegundosParaReintentar()));
        }
        return respuesta.body(error(ex.getStatus(), ex.getMessage(), request, ex.getCodigo()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errores = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return responder(HttpStatus.BAD_REQUEST, "Error de validación: " + errores, request);
    }

    // Validaciones sobre @RequestParam / @PathVariable (por ejemplo @Size en el motivo de una cancelación).
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        String errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return responder(HttpStatus.BAD_REQUEST, "Error de validación: " + errores, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String errores = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return responder(HttpStatus.BAD_REQUEST, "Error de validación: " + errores, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Cubre, entre otros casos, fechas con un año fuera de lo razonable escritas a mano
        // (ej. "20260-01-01" con 5 dígitos): Jackson no logra parsear el LocalDate/LocalDateTime
        // y lanza esta excepción antes de que el DTO llegue a las validaciones de Bean Validation.
        log.warn("Cuerpo de la solicitud inválido en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud tiene un formato inválido. Revise que las fechas y números tengan un formato correcto (por ejemplo, el año no puede tener más de 4 dígitos).",
                request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas", request);
    }

    // Login de una cuenta desactivada (DisabledException) u otro fallo de autenticación distinto de
    // credenciales incorrectas: antes caía en el handler genérico y respondía 500.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String mensaje = ex instanceof DisabledException
                ? "La cuenta está desactivada. Contactá al club."
                : "No se pudo autenticar al usuario";
        return responder(HttpStatus.UNAUTHORIZED, mensaje, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso", request);
    }

    // Parámetro de path/query con formato inválido (ej. /rankings/NOVENA, ?fecha=17-10-2026, ?cancha=abc).
    // No se repite el valor recibido en la respuesta.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "El valor indicado no es válido para el parámetro '" + ex.getName() + "'", request);
    }

    // Violación de una restricción de la base (unicidad, FK, superposición): es un conflicto de datos, no un error interno.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violación de integridad de datos en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "La operación entra en conflicto con datos existentes (registro duplicado o en uso).", request);
    }

    // Dos operaciones modificaron el mismo registro a la vez (bloqueo optimista / pesimista).
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            CannotAcquireLockException.class})
    public ResponseEntity<ApiError> handleConcurrencia(RuntimeException ex, HttpServletRequest request) {
        log.info("Conflicto de concurrencia en {}: {}", request.getRequestURI(), ex.getMessage());
        return responder(HttpStatus.CONFLICT,
                "Otra operación modificó estos datos al mismo tiempo. Actualizá la pantalla e intentá de nuevo.", request);
    }

    @ExceptionHandler(PasarelaPagosException.class)
    public ResponseEntity<ApiError> handlePasarela(PasarelaPagosException ex, HttpServletRequest request) {
        log.error("Error con la pasarela de pagos en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return responder(HttpStatus.BAD_GATEWAY,
                "No se pudo completar la operación con MercadoPago. Intentá nuevamente en unos minutos.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        // Excepciones propias de Spring MVC que ya traen su status (ruta inexistente -> 404, método no
        // soportado -> 405, falta un parámetro obligatorio -> 400, content-type inválido -> 415...).
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_REQUEST;
            }
            return responder(status, mensajeParaErrorDeFramework(status), request);
        }
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Por favor intente nuevamente.", request);
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, String mensaje, HttpServletRequest request) {
        return ResponseEntity.status(status).body(error(status, mensaje, request, null));
    }

    private ApiError error(HttpStatus status, String mensaje, HttpServletRequest request, String codigo) {
        return new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), mensaje, request.getRequestURI(), codigo);
    }

    private String mensajeParaErrorDeFramework(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe";
            case METHOD_NOT_ALLOWED -> "Método HTTP no soportado para este recurso";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
            case NOT_ACCEPTABLE -> "Formato de respuesta no soportado";
            case PAYLOAD_TOO_LARGE -> "La solicitud es demasiado grande";
            default -> "La solicitud es inválida o está incompleta";
        };
    }
}
