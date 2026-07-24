# ===== Etapa de build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachea dependencias: solo se re-descargan si cambia el pom.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ===== Etapa de runtime =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario no-root por seguridad.
RUN addgroup -S padel && adduser -S padel -G padel

COPY --from=build /app/target/padelapp-backend-0.0.1-SNAPSHOT.jar app.jar
USER padel

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
