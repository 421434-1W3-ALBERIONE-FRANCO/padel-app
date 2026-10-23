# Dockerfile del backend con el repo entero como contexto, para plataformas que buscan un "Dockerfile" en la
# raíz (Render y similares). Es el mismo que backend/Dockerfile, que es el que usa docker-compose.yml.

# ===== Etapa de build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachea dependencias: solo se re-descargan si cambia el pom.
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
# Los tests corren en CI (mvn verify); acá solo se empaqueta.
RUN mvn -B -q package -Dmaven.test.skip=true

# ===== Etapa de runtime =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario no-root por seguridad.
RUN addgroup -S padel && adduser -S padel -G padel

# Comodín *.jar: no depende del número de versión del artefacto.
COPY --from=build /app/target/*.jar app.jar
USER padel

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT:-8081}/actuator/health" || exit 1

# Usa el puerto que asigna la plataforma ($PORT); 8080 por defecto.
ENTRYPOINT ["sh", "-c", "exec java -jar app.jar --server.port=${PORT:-8080}"]
