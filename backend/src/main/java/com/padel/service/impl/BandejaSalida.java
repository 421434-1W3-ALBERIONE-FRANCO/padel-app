package com.padel.service.impl;

import com.padel.domain.entity.MensajeSaliente;
import com.padel.domain.enums.CanalMensaje;
import com.padel.repository.MensajeSalienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Encola mensajes en la bandeja de salida dentro de la transacción en curso: si la operación de negocio se
 * revierte, el mensaje también (no se avisa de algo que no pasó).
 */
@Service
@RequiredArgsConstructor
public class BandejaSalida {

    private final MensajeSalienteRepository repository;

    @Transactional
    public void encolarEmail(String destinatario, String asunto, String html) {
        encolar(CanalMensaje.EMAIL, destinatario, asunto, html);
    }

    @Transactional
    public void encolarWhatsApp(String telefono, String texto) {
        encolar(CanalMensaje.WHATSAPP, telefono, null, texto);
    }

    private void encolar(CanalMensaje canal, String destinatario, String asunto, String cuerpo) {
        repository.save(MensajeSaliente.builder()
                .canal(canal)
                .destinatario(destinatario)
                .asunto(asunto)
                .cuerpo(cuerpo)
                .proximoIntentoEn(LocalDateTime.now())
                .build());
    }
}
