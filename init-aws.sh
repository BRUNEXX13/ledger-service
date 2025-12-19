#!/bin/bash
echo "Iniciando configuração do LocalStack..."

# Criar Bucket S3
echo "Criando bucket S3: ledger-bucket"
awslocal s3 mb s3://ledger-bucket

# Criar Fila SQS
echo "Criando fila SQS: ledger-queue"
awslocal sqs create-queue --queue-name ledger-queue

# Criar Tópico SNS
echo "Criando tópico SNS: ledger-topic"
awslocal sns create-topic --name ledger-topic

# (Opcional) Inscrever a fila no tópico
echo "Inscrevendo fila no tópico..."
awslocal sns subscribe \
    --topic-arn arn:aws:sns:us-east-1:000000000000:ledger-topic \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-east-1:000000000000:ledger-queue

echo "Configuração do LocalStack concluída!"
