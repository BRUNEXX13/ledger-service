# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

# Stage 2: Create the final, smaller image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Cria o diretório de logs para garantir que ele exista
RUN mkdir logs

COPY --from=build /app/target/*.jar app.jar
# A porta da aplicação é 8082, conforme application.properties
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
