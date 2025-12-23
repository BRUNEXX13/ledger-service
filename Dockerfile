# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-23 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

# Stage 2: Create the final, smaller image
FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

# Cria o diretório de logs
RUN mkdir logs

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082

# --- JVM TUNING EXPLAINED ---
# -XX:+UseZGC: Garbage Collector de baixa latência (<1ms pauses).
# -XX:+ZGenerational: (Java 21+) Separa gerações Young/Old. CRUCIAL para performance de apps web.
# -XX:+UseStringDeduplication: Otimiza memória reduzindo strings duplicadas (ótimo para JSON).
# -XX:MaxRAMPercentage=80.0: Usa 80% da RAM do container para o Heap (melhor que -Xmx fixo).
# -Djava.security.egd=file:/dev/./urandom: Evita bloqueio por falta de entropia na geração de UUIDs/SSL.
# -Djdk.tracePinnedThreads=short: Monitora problemas com Virtual Threads.

ENTRYPOINT [ \
    "java", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-XX:+UseStringDeduplication", \
    "-XX:MaxRAMPercentage=80.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Djdk.tracePinnedThreads=short", \
    "-Dspring.threads.virtual.enabled=true", \
    "-jar", \
    "app.jar" \
]
