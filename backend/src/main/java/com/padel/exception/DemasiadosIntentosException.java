package com.padel.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Se superó un límite de pedidos (fuerza bruta en el login, envío de emails, altas de reservas). */
@Getter
public class DemasiadosIntentosException extends NegocioException {

    public static final String CODIGO = "DEMASIADOS_INTENTOS";

    private final long segundosParaReintentar;

    public DemasiadosIntentosException(String message, long segundosParaReintentar) {
        super(message);
        this.segundosParaReintentar = segundosParaReintentar;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    @Override
    public String getCodigo() {
        return CODIGO;
    }
}
