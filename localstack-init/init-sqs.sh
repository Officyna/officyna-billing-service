#!/bin/sh
set -e

echo "Criando recursos de mensageria do billing-service no LocalStack..."

awslocal sqs create-queue --queue-name officyna-billing-requests
awslocal sqs create-queue --queue-name officyna-billing-responses

TOPIC_ARN=$(awslocal sns create-topic --name officyna-billing-order-paid --query TopicArn --output text)
echo "Tópico de aprovação criado: $TOPIC_ARN"

# Fila de teste só pra inspecionar localmente o que é publicado no tópico, sem precisar
# de outro serviço rodando (os-service/production-service ainda não consomem isso de verdade).
TEST_QUEUE_URL=$(awslocal sqs create-queue --queue-name officyna-billing-order-paid-test --query QueueUrl --output text)
TEST_QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url "$TEST_QUEUE_URL" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$TEST_QUEUE_ARN"

echo "Recursos de mensageria prontos."
