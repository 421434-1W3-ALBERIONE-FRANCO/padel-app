package com.padel.service;

import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Usuario;

public interface EmailService {
    void enviarBienvenida(Usuario usuario);
    void enviarComprobantePago(Pago pago);
    void enviarConfirmacionCambioPassword(Usuario usuario);
    void enviarRecuperacionPassword(Usuario usuario, String tokenPlano);
}
