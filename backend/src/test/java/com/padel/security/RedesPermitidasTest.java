package com.padel.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedesPermitidasTest {

    @Test
    void sinConfiguracionNoRestringe() {
        RedesPermitidas redes = new RedesPermitidas("");

        assertFalse(redes.hayRestriccion());
        assertTrue(redes.permite("8.8.8.8"));
        assertTrue(redes.permite(null));
    }

    @Test
    void respetaElPrefijoDeCadaRed() {
        RedesPermitidas redes = new RedesPermitidas("192.168.1.0/24, 10.0.0.7, 172.16.0.0/12");

        assertTrue(redes.permite("192.168.1.1"));
        assertTrue(redes.permite("192.168.1.254"));
        assertFalse(redes.permite("192.168.2.1"));
        assertTrue(redes.permite("10.0.0.7"));
        assertFalse(redes.permite("10.0.0.8"));
        assertTrue(redes.permite("172.31.255.255"));
        assertFalse(redes.permite("172.32.0.1"));
    }

    @Test
    void ipVaciaOInvalidaNoPasaCuandoHayRestriccion() {
        RedesPermitidas redes = new RedesPermitidas("192.168.1.0/24");

        assertFalse(redes.permite(null));
        assertFalse(redes.permite(" "));
        assertFalse(redes.permite("2001:db8::1"));
    }

    @Test
    void admiteRedesIpv6() {
        RedesPermitidas redes = new RedesPermitidas("2001:db8::/32");

        assertTrue(redes.permite("2001:db8:1::5"));
        assertFalse(redes.permite("2001:db9::1"));
        assertFalse(redes.permite("192.168.1.1"));
    }

    @Test
    void unaRedMalEscritaFallaAlArrancar() {
        assertThrows(IllegalArgumentException.class, () -> new RedesPermitidas("192.168.1.0/33"));
        assertThrows(IllegalArgumentException.class, () -> new RedesPermitidas("192.168.1.0/abc"));
    }
}
