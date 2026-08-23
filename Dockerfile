FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Tests lancés en CI / en local (mvn test). Skip ici : Testcontainers
# ne peut pas démarrer Docker pendant le build de l'image.
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Durcissement ANSSI : exécution du service sous un compte non-root dédié.
RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

# Répertoires logs + média (volume monté par-dessus /data/media en compose)
RUN mkdir -p /app/logs /data/media /app/data/media \
    && chown -R spring:spring /app/logs /data/media /app/data/media \
    && chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

# Entrée root uniquement pour chown du volume, puis bascule sur spring.
USER root
ENTRYPOINT ["/app/docker-entrypoint.sh"]
