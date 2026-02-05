# ===== STAGE 1 - Build da aplicação =====
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ===== STAGE 2 - Runtime =====
FROM eclipse-temurin:21-jdk

# Instalar Alloy
RUN apt-get update && apt-get install -y curl
RUN curl -L https://github.com/grafana/alloy/releases/latest/download/alloy-linux-amd64 -o /usr/local/bin/alloy \
    && chmod +x /usr/local/bin/alloy

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Script de entrada
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
