# Dockerfile en la RAIZ del repo, pensado para Render (que busca "Dockerfile" en
# la raiz cuando se elige el runtime Docker sin Root Directory).
# El contexto de build es todo el repo, por eso los COPY apuntan a backend/.

# ===== Etapa de build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachea dependencias: solo se re-descargan si cambia el pom.
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
RUN mvn -B -q clean package -DskipTests

# ===== Etapa de runtime =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario no-root por seguridad.
RUN addgroup -S padel && adduser -S padel -G padel

# Comodin *.jar: no depende del numero de version del artefacto (0.0.1-SNAPSHOT, etc.).
COPY --from=build /app/target/*.jar app.jar
USER padel

EXPOSE 8080
# Usa el puerto que asigna Render ($PORT); 8080 por defecto en local.
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
