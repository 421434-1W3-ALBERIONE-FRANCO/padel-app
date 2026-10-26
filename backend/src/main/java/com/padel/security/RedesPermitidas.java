package com.padel.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Redes desde las que el staff puede fichar ({@code app.control-horario.redes-permitidas}, CIDR separados por
 * coma, ej. "192.168.1.0/24, 10.0.0.7"). Vacío = sin restricción. Las horas fichadas se pagan en la
 * liquidación: antes la jornada empezaba con el login y alcanzaba con entrar desde casa para sumar horas extra.
 */
@Component
public class RedesPermitidas {

    private final List<Red> redes;

    public RedesPermitidas(@Value("${app.control-horario.redes-permitidas:}") String configuracion) {
        this.redes = Arrays.stream(configuracion == null ? new String[0] : configuracion.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(Red::parse)
                .toList();
    }

    public boolean hayRestriccion() {
        return !redes.isEmpty();
    }

    public boolean permite(String ip) {
        if (redes.isEmpty()) {
            return true;
        }
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            byte[] direccion = InetAddress.getByName(ip.trim()).getAddress();
            return redes.stream().anyMatch(red -> red.contiene(direccion));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    record Red(byte[] base, int prefijo) {

        static Red parse(String cidr) {
            String[] partes = cidr.split("/");
            try {
                byte[] base = InetAddress.getByName(partes[0]).getAddress();
                int prefijo = partes.length > 1 ? Integer.parseInt(partes[1]) : base.length * 8;
                if (prefijo < 0 || prefijo > base.length * 8) {
                    throw new IllegalArgumentException("Prefijo inválido en " + cidr);
                }
                return new Red(base, prefijo);
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("Red inválida en app.control-horario.redes-permitidas: " + cidr, e);
            }
        }

        boolean contiene(byte[] direccion) {
            if (direccion.length != base.length) {
                return false; // IPv4 contra IPv6
            }
            int bitsCompletos = prefijo / 8;
            for (int i = 0; i < bitsCompletos; i++) {
                if (direccion[i] != base[i]) {
                    return false;
                }
            }
            int resto = prefijo % 8;
            if (resto == 0) {
                return true;
            }
            int mascara = (0xFF << (8 - resto)) & 0xFF;
            return (direccion[bitsCompletos] & mascara) == (base[bitsCompletos] & mascara);
        }
    }
}
