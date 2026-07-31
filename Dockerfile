FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Exécute les tests unitaires avant le package (ne pas skipTests).
RUN mvn clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Durcissement ANSSI : exécution du service sous un compte non-root dédié.
RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

# Répertoire de logs accessible en écriture par le compte non-root
RUN mkdir -p /app/logs \
    && chown -R spring:spring /app/logs

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
