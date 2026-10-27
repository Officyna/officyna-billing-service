# officyna-billing-service

Microsserviço financeiro e de pagamentos da Officyna — orçamentos, faturamento, conciliação e
integração com o Mercado Pago (Checkout Transparente / Orders API).

## Arquitetura

Comunicação híbrida:

- **Assíncrona (SQS/SNS)** — geração do orçamento inicial e notificação final de pagamento.
- **Síncrona (REST)** — aprovação final + geração do checkout, porque quem chama precisa do
  QR Code/dado do pagamento imediatamente pra agir em cima.
- **Webhook (REST)** — o Mercado Pago chama essa URL direto pela internet; é o único endpoint
  que não dá pra trocar por mensageria, é o provedor externo quem decide como notifica.

### Fluxo

1. `os-service` publica **`eventGeneratePayment`** (fila `officyna-billing-requests`) com os
   `labors`/`supplies` levantados no diagnóstico.
2. Billing-service consome, salva os itens com situação `PENDING_APPROVAL`, calcula ISS (sobre
   mão de obra) e ICMS (sobre peças/insumos), publica **`eventBudgetGenerated`** (fila
   `officyna-billing-responses`) com os totais.
3. Depois que o cliente aprova (fora deste serviço), `os-service` chama **síncrono**
   `POST /api/v1/checkout/create` só com os itens aprovados — billing-service marca o resto como
   `REJECTED` (preserva histórico), recalcula o total, gera a cobrança no Mercado Pago (Pix QR
   Code ou token de cartão já tokenizado por quem chama) e responde na hora.
4. O Mercado Pago confirma o pagamento via **webhook** (`POST /api/v1/payments/webhook`) —
   valida assinatura HMAC-SHA256, consulta o status real, mapeia pra `PAID`/`WAITING_PAYMENT`/
   `FAILED`.
5. Quando `PAID`, publica **`eventOrderPaid`** no tópico SNS `officyna-billing-order-paid` —
   `os-service` e `production-service` consomem em paralelo, cada um na própria fila inscrita.

Billing-service é dono só dos próprios recursos de mensageria (as 2 filas que usa + o tópico que
publica), criados de forma idempotente (`CreateQueue`/`CreateTopic` — se já existe, só devolve).
Quem quiser escutar o tópico cuida da própria fila/inscrição.

## Stack

Java 21, Spring Boot 4.0.3, Maven, Postgres/Flyway, AWS SDK v2 (SQS/SNS). Sem JDK/Maven local?
Use o container oficial:

```bash
docker run --rm -v "$PWD":/app -w /app -v maven-repo-cache:/root/.m2 \
  maven:3.9.9-eclipse-temurin-21 mvn verify
```

## Rodando localmente (Postgres + LocalStack + app)

```bash
MERCADOPAGO_ACCESS_TOKEN="<seu access token de teste>" \
MERCADOPAGO_WEBHOOK_SECRET="<secret do painel Webhooks>" \
docker compose up -d
```

Sobe Postgres (Flyway migra o schema automaticamente), LocalStack (SQS/SNS — `localstack-init/
init-sqs.sh` cria as filas/tópico, mais uma fila de teste inscrita no tópico pra inspecionar
localmente) e a aplicação em `http://localhost:8086` (porta do host; o container escuta 8080 —
8086 é só pra não colidir com outra coisa já usando 8080 na sua máquina, ajuste à vontade).

Swagger UI em `http://localhost:8086/swagger-ui.html`. Health check em `/actuator/health`.

### Testar a mensageria manualmente

```bash
QUEUE_URL=$(docker exec officyna-billing-localstack awslocal sqs get-queue-url --queue-name officyna-billing-requests --query QueueUrl --output text)
docker exec officyna-billing-localstack awslocal sqs send-message --queue-url "$QUEUE_URL" --message-body '{
  "correlationId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
  "serviceOrderId": "6724a1b8e4b0123456789abc",
  "labors": [{"laborId": "lab_1", "description": "Troca de pastilha de freio", "amount": 150.00, "situation": "PENDING_APPROVAL"}],
  "supplies": [{"supplyId": "sup_1", "description": "Jogo de pastilhas Bosch", "amount": 180.00, "situation": "PENDING_APPROVAL"}]
}'
```

O app consome em até 5s (polling), calcula o orçamento e publica `eventBudgetGenerated` em
`officyna-billing-responses` — dá pra ler com `awslocal sqs receive-message --queue-url ...`.

### Checkout

```bash
curl -X POST http://localhost:8086/api/v1/checkout/create \
  -H 'Content-Type: application/json' \
  -d '{
    "correlationId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
    "serviceOrderId": "6724a1b8e4b0123456789abc",
    "paymentMethod": "PIX",
    "payerEmail": "test_user_<BUYER_USER_ID>@testuser.com",
    "approvedLabors": [{"laborId": "lab_1", "amount": 150.00}],
    "approvedSupplies": [{"supplyId": "sup_1", "amount": 180.00}]
  }'
```

Pra cartão (`paymentMethod: CREDIT_CARD` ou `DEBIT_CARD`), inclua `cardToken` (gerado via
`POST /v1/card_tokens` do Mercado Pago, ver abaixo), `cardBrand` (ex.: `"master"`) e
`installments`. `cardToken`/`cardBrand`/`installments` não estavam no desenho original do
documento da weekly (só tinha o exemplo de Pix) — são extensões necessárias pro Checkout
Transparente de cartão funcionar, já que a tokenização acontece em quem chama, billing-service
nunca vê o cartão cru.

```bash
curl -X POST "https://api.mercadopago.com/v1/card_tokens?public_key=<sua public key de teste>" \
  -H 'Content-Type: application/json' \
  -d '{
    "card_number": "5480832801033311",
    "security_code": "123",
    "expiration_month": 11,
    "expiration_year": 2030,
    "cardholder": { "name": "APRO", "identification": { "type": "CPF", "number": "12345678909" } }
  }'
```

### Webhook

Mercado Pago assina a notificação com HMAC-SHA256 no header `x-signature`
(`ts=<timestamp>,v1=<hash>`), calculado sobre o manifest `id:{data.id};request-id:{x-request-id};ts:{ts};`.
`data.id` é o **id do pedido** (Orders API — `/v1/payments/{id}` não existe pra pedidos criados
via Orders API, só `/v1/orders/{id}`), vem como **query param** na própria URL do webhook
(`?data.id=...&type=payment`), e entra no manifest **exatamente como veio** (a documentação do
Mercado Pago diz que deveria ser lowercase quando alfanumérico, mas isso não bateu com o id de
pedido real, que tem maiúsculas — confirmado contra uma notificação real via ngrok).

```bash
curl -X POST "http://localhost:8086/api/v1/payments/webhook?data.id=<order id>&type=payment" \
  -H 'Content-Type: application/json' \
  -H 'x-signature: ts=...,v1=...' \
  -H 'x-request-id: sim-req-001' \
  -d '{"type":"payment","action":"order.updated","data":{"id":"<order id>"}}'
```

## Notas de implementação (Spring Boot 4 é recente — várias peças do ecossistema ainda não acompanharam)

- **Jackson 3** (`tools.jackson`, não `com.fasterxml.jackson`) é o padrão nos conversores HTTP —
  os gateways usam `tools.jackson.databind.JsonNode`/`ObjectMapper`.
- **Sem Spring Cloud AWS**: não existe versão de `spring-cloud-aws-starter-sqs`/`-sns`
  compatível com Spring Boot 4 no Maven Central ainda (a mais recente, 3.4.0, é pro trem do
  Boot 3.4.x). Mensageria usa `software.amazon.awssdk:sqs`/`sns` (SDK v2 puro) com polling via
  `@Scheduled`, mesmo padrão que o `officyna-os-service` já usa na branch dele.
- **Sem autoconfiguração do Flyway**: o Boot 4.0.3 modularizou a autoconfiguração (existem
  `spring-boot-hibernate`/`spring-boot-jpa`/`spring-boot-mongodb`, mas nenhum
  `spring-boot-flyway`) — sem isso, o JPA tentava validar o schema antes da migração rodar.
  `infrastructure/config/FlywayConfig` conecta o Flyway na mão: define o próprio bean
  `DataSource` e roda `flyway.migrate()` como efeito colateral da criação dele, garantindo a
  ordem certa só com injeção de dependência normal (uma tentativa anterior via
  `BeanFactoryPostProcessor`/`@DependsOn` esbarrou em resolução prematura do FactoryBean do
  JPA — ver histórico do arquivo se for mexer nisso).
- Chamadas à Orders API usam `RestClient` com corpo montado como `Map` aninhado em vez de DTOs
  tipados — o payload da Orders API tem muitos campos opcionais que não valem a pena modelar.
- `serviceOrderId` é tratado como `String` (não `UUID`) em todo o schema/domínio — o `os-service`
  usa MongoDB, o id da O.S. é um ObjectId (formato `6724a1b8e4b0123456789abc`), não um UUID.

## Testando

```bash
docker run --rm -v "$PWD":/app -w /app -v maven-repo-cache:/root/.m2 \
  maven:3.9.9-eclipse-temurin-21 mvn verify
```

Testes unitários (JUnit 5 + Mockito) cobrindo o cálculo de orçamento/impostos, a aprovação de
itens, os gateways de persistência e o fluxo de webhook. Gate do Jacoco em 80% de cobertura de
linha (exclui camadas finas: `api`, `config`, `messaging`, entidades JPA).

## Pontos em aberto

- Reconciliar `BudgetStatus` (nível orçamento) com `ItemSituation`+`PaymentStatus` — avaliar se
  `BudgetStatus` é redundante.
- Alíquotas de ISS/ICMS (`billing.tax.iss-rate`/`icms-rate`) são percentuais fixos
  configuráveis, sem regra fiscal real por município/estado (fora de escopo do projeto).
- `POST /checkout/create` ainda não é idempotente — uma chamada em duplicidade (retry do
  os-service) geraria dois checkouts pro mesmo orçamento.
- Infra real na AWS (RDS Postgres, filas/tópico de verdade) ainda não provisionada — o pipeline
  de CI/CD já espera um parâmetro SSM (`/officyna/billing-db/endpoint`) que ainda não existe.
- Contrato de mensageria com `officyna-os-service`/`officyna-execution-production` ainda não
  reconciliado entre os três serviços — cada um tem seu próprio rascunho em branches não
  mergeadas; este documento é a fonte da verdade pro lado do billing-service.
