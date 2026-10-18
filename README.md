# officyna-billing-service (POC)

POC de integração com o Mercado Pago (Checkout Transparente / Orders API) para o
`officyna-billing-service`. Cobre apenas os dois fluxos que dependem do Mercado Pago —
checkout Pix e checkout com cartão de crédito — mais o webhook de confirmação de pagamento.

**Fora do escopo desta POC** (fica para a próxima etapa, junto com o restante do TASK-02):
persistência relacional (Postgres/Flyway), geração de orçamento, aprovação/rejeição de OS.
O status do pagamento aqui fica em memória (`PaymentStatusStore`), só para validar a integração.

## Stack

Java 21, Spring Boot 4.0.3, Maven. Sem JDK/Maven local? Use o container oficial:

```bash
docker run --rm -v "$PWD":/app -w /app -v maven-repo-cache:/root/.m2 \
  maven:3.9.9-eclipse-temurin-21 mvn test
```

## Rodando localmente

Precisa de uma aplicação de teste no [painel do Mercado Pago](https://www.mercadopago.com.br/developers/panel)
(solução "Checkout Transparente" → tipo de API "API de Orders"). Nas **Credenciais de teste**,
pegue o Access Token; em **Contas de teste**, use um usuário "Comprador" (não dá pra pagar com
a própria conta vendedora — erro `invalid_users_involved`). O e-mail do comprador segue o padrão
`test_user_<USER_ID>@testuser.com`.

```bash
docker run --rm --name officyna-billing-poc \
  -p 8085:8085 \
  -e MERCADOPAGO_ACCESS_TOKEN="<seu access token de teste>" \
  -e MERCADOPAGO_WEBHOOK_SECRET="<secret do painel Webhooks, ou qualquer valor para simulação local>" \
  -v "$PWD":/app -w /app -v maven-repo-cache:/root/.m2 \
  maven:3.9.9-eclipse-temurin-21 mvn -q spring-boot:run
```

Swagger UI em `http://localhost:8085/swagger-ui.html`.

## Checkout Pix

```bash
curl -X POST http://localhost:8085/api/v1/payments/checkout/pix \
  -H 'Content-Type: application/json' \
  -d '{"amount": 50.00, "payerEmail": "test_user_<BUYER_USER_ID>@testuser.com"}'
```

Retorna `qrCode` (copia-e-cola) e `qrCodeBase64` (imagem).

## Checkout cartão

Gere um token de cartão de teste direto via API (sem precisar do SDK JS no browser — útil
para testes automatizados). Números de cartão de teste e os prefixos de nome do titular que
simulam cada status (`APRO` = aprovado, `OTHE` = erro geral, `FUND` = saldo insuficiente etc.)
estão em **Cartões de teste** no painel.

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

```bash
curl -X POST http://localhost:8085/api/v1/payments/checkout/card \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 50.00,
    "payerEmail": "test_user_<BUYER_USER_ID>@testuser.com",
    "cardToken": "<id retornado acima>",
    "paymentMethodId": "master",
    "installments": 1
  }'
```

## Webhook

Mercado Pago assina a notificação com HMAC-SHA256 no header `x-signature`
(`ts=<timestamp>,v1=<hash>`), calculado sobre o manifest `id:{data.id};request-id:{x-request-id};ts:{ts};`.
Para a Orders API, `data.id` é o **id do pedido** (não um id de pagamento legado — o endpoint
`/v1/payments/{id}` não existe para pedidos criados via Orders API, só `/v1/orders/{id}`), e vem
como **query param** na própria URL do webhook (`?data.id=...&type=payment`), não só no corpo.

Confirmado contra uma notificação real (via ngrok + "Simular notificação" do painel): `data.id`
entra no manifest **exatamente como veio** — a documentação do Mercado Pago diz que deveria ser
lowercase quando alfanumérico, mas isso não bateu com o id de pedido real (`ORDTST...`), que tem
maiúsculas. Forçar lowercase quebra a validação.

Para simular localmente sem expor a porta publicamente:

```python
import hmac, hashlib, time

data_id = "<order id>"
request_id = "sim-req-001"
ts = str(int(time.time()))
secret = "<MERCADOPAGO_WEBHOOK_SECRET usado ao subir a app>"

manifest = f"id:{data_id};request-id:{request_id};ts:{ts};"
v1 = hmac.new(secret.encode(), manifest.encode(), hashlib.sha256).hexdigest()
print(f"x-signature: ts={ts},v1={v1}")
```

```bash
curl -X POST "http://localhost:8085/api/v1/payments/webhook?data.id=<order id>&type=payment" \
  -H 'Content-Type: application/json' \
  -H 'x-signature: ts=...,v1=...' \
  -H 'x-request-id: sim-req-001' \
  -d '{"type":"payment","action":"order.updated","data":{"id":"<order id>"}}'
```

Validado contra o callback real do Mercado Pago via `ngrok http 8085` + URL configurada em
**Webhooks → Configurar notificações** no painel, usando o secret real de lá. O painel também
tem um botão **"Simular notificação"** que dispara uma chamada real (assinada de verdade) pra
URL configurada, sem precisar completar um pagamento de verdade — só é preciso trocar o "Data ID"
pelo id de um pedido que o `PaymentStatusStore` em memória já conheça (criado numa chamada
anterior a `/checkout/pix` ou `/checkout/card` nessa mesma instância da aplicação).

## Consultar status local

```bash
curl http://localhost:8085/api/v1/payments/{orderId}
```

## Notas de implementação

- Spring Boot 4 / Spring Framework 7 trocou o Jackson clássico (`com.fasterxml.jackson`) pelo
  Jackson 3 (`tools.jackson`) nos conversores HTTP padrão — os gateways usam
  `tools.jackson.databind.JsonNode`, não a classe antiga.
- Chamadas à Orders API usam `RestClient` com corpo montado como `Map` aninhado em vez de DTOs
  tipados — o payload da Orders API tem muitos campos opcionais que não valem a pena modelar
  numa POC.

## Próxima etapa (arquitetura em definição com o time)

O fluxo completo do billing-service (diagrama de sequência alinhado na weekly) é guiado por
mensageria, não por REST público:

- Consome `eventGeneratePayment` (publicado pelo `production-service` logo após o diagnóstico
  ser finalizado, com os `labors`/`supplies` aprovados) → calcula o orçamento inicial → publica
  `eventBugetGenerated` (sem link de pagamento ainda).
- Depois que o cliente aprova a O.S, consome `eventGeneratePayment` de novo (dessa vez publicado
  pelo `os-service`) → recalcula o orçamento → só agora integra com o Mercado Pago pra gerar o
  link de pagamento → publica `eventBugetGenerated` novamente, agora com `paymentLink`.
- O webhook do Mercado Pago (já implementado neste repo) é o ponto de entrada da notificação de
  pagamento. Ao receber, publica (via **SNS**) `topicAprovacao {serviceOrderId, statusPayment}`,
  que `os-service` e `production-service` consomem em paralelo (fanout).

Esse contrato de mensageria ainda não está implementado neste repositório — os nomes de evento
acima vêm do diagrama de sequência compartilhado pelo time, e divergem (em nomenclatura) do que
já está esboçado em branches de feature dos serviços vizinhos:
`officyna-os-service` (branch `181/feat/implementar-comunicacao-assincrona`, usa AWS SDK puro,
espera um `PaymentConfirmedEvent` na própria fila `os-service-queue`) e
`officyna-execution-production` (branch `feature/configurando-repo`, usa Spring Cloud AWS,
já define `billing-request-queue`/`billing-response-queue` com `ExecutionEvent`/
`BillingDecisionEvent`). Alinhar isso é trabalho da próxima rodada, não desta POC.
