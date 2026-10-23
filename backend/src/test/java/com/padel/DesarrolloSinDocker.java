package com.padel;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Backend de desarrollo sin Docker: levanta un PostgreSQL embebido (los mismos binarios que usan los tests) en el
 * puerto y con las credenciales que espera el perfil dev, y arranca la aplicación con ese perfil.
 *
 * <pre>mvn exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.padel.DesarrolloSinDocker</pre>
 *
 * Los datos quedan en {@code backend/.pg-dev} (borrar la carpeta para empezar de cero). Si el proceso se cortó a la
 * fuerza y el Postgres embebido anterior sigue vivo, se reutiliza. Redis es opcional: sin él, los límites por IP y
 * la revocación de sesiones usan memoria y el lock de horarios cede al control de la base.
 */
public final class DesarrolloSinDocker {

    private static final int PUERTO = 5433;
    private static final String URL_ADMIN = "jdbc:postgresql://localhost:" + PUERTO + "/postgres";

    private DesarrolloSinDocker() {
    }

    public static void main(String[] args) throws Exception {
        if (!hayPostgresEmbebidoCorriendo()) {
            EmbeddedPostgres.builder()
                    .setPort(PUERTO)
                    .setDataDirectory(carpetaDeDatos())
                    .setCleanDataDirectory(false)
                    .start();
        }
        try (Connection conexion = DriverManager.getConnection(URL_ADMIN, "postgres", "");
             Statement sentencia = conexion.createStatement()) {
            if (!existe(sentencia, "SELECT 1 FROM pg_roles WHERE rolname = 'padel'")) {
                sentencia.execute("CREATE ROLE padel LOGIN PASSWORD 'padel1234'");
            }
            if (!existe(sentencia, "SELECT 1 FROM pg_database WHERE datname = 'padeldb'")) {
                sentencia.execute("CREATE DATABASE padeldb OWNER padel");
            }
        }
        PadelApplication.main(Stream.concat(Stream.of("--spring.profiles.active=dev"), Arrays.stream(args))
                .toArray(String[]::new));
    }

    /** El embebido acepta al superusuario "postgres" sin clave; un Postgres con contraseña en el puerto, no. */
    private static boolean hayPostgresEmbebidoCorriendo() {
        try (Connection ignorada = DriverManager.getConnection(URL_ADMIN, "postgres", "")) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** backend/.pg-dev, se lance desde la raíz del repo (mvn -f backend/pom.xml) o desde backend. */
    private static Path carpetaDeDatos() {
        Path modulo = Files.exists(Path.of("backend", "pom.xml")) ? Path.of("backend") : Path.of(".");
        return modulo.resolve(".pg-dev").toAbsolutePath();
    }

    private static boolean existe(Statement sentencia, String consulta) throws Exception {
        try (ResultSet resultado = sentencia.executeQuery(consulta)) {
            return resultado.next();
        }
    }
}
