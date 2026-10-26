# ===== Etapa de build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachea dependencias: solo se re-descargan si cambia el pom.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Los tests corren en CI (mvn verify); acá solo se empaqueta.
RUN mvn -B -q package -Dmaven.test.skip=true

# ===== Etapa de runtime =====
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Usuario no-root por seguridad.
RUN addgroup -S padel && adduser -S padel -G padel

# Comodín *.jar: no depende del número de versión del artefacto.
COPY --from=build /app/target/*.jar app.jar
USER padel

# La imagen es para servidores: arranca siempre con el perfil prod (sin defaults inseguros; si falta un
# secreto no arranca). Para desarrollo se usa mvn spring-boot:run con el perfil dev.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
# Health en el puerto de management (8081), que no se publica fuera de la red de Docker.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT:-8081}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
