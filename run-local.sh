#!/bin/bash

# Define o nome do arquivo do agente
AGENT_JAR="dd-java-agent.jar"

# Verifica se o agente já existe, se não, baixa
if [ ! -f "$AGENT_JAR" ]; then
    echo "Baixando Datadog Java Agent..."
    wget -O $AGENT_JAR https://dtdg.co/latest-java-tracer
fi

# Compila o projeto (opcional, pode comentar se preferir rodar mvn package antes)
echo "Compilando o projeto..."
./mvnw clean package -DskipTests

# Encontra o JAR da aplicação (pega o primeiro que encontrar na pasta target)
APP_JAR=$(find target -name "*.jar" | head -n 1)

if [ -z "$APP_JAR" ]; then
    echo "Erro: JAR da aplicação não encontrado. A compilação falhou?"
    exit 1
fi

echo "Iniciando a aplicação com Datadog Agent..."

# Roda a aplicação com as configurações do Datadog
java -javaagent:./$AGENT_JAR \
     -Ddd.service=ledger-service-local \
     -Ddd.env=development \
     -Ddd.agent.host=localhost \
     -jar $APP_JAR
