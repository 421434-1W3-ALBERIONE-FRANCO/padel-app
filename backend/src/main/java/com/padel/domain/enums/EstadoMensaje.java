package com.padel.domain.enums;

public enum EstadoMensaje {
    PENDIENTE,
    ENVIADO,
    /** Se agotaron los reintentos: queda registrado para revisarlo. */
    ERROR
}
