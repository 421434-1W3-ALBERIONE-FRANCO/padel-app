package com.padel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PadelApplication {

    // Zona horaria del club. El contenedor de Render corre en UTC: sin esto "hoy", los
    // horarios pasados de la grilla, las validaciones @Future y los cron (recordatorios,
    // cierre de sesiones) quedan corridos 3 horas. Se puede sobreescribir con APP_TIMEZONE.
    private static final String ZONA_HORARIA_DEFAULT = "America/Argentina/Buenos_Aires";

    public static void main(String[] args) {
        String zona = System.getenv().getOrDefault("APP_TIMEZONE", ZONA_HORARIA_DEFAULT);
        TimeZone.setDefault(TimeZone.getTimeZone(zona));
        SpringApplication.run(PadelApplication.class, args);
    }
}
