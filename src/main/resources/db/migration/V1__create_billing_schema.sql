CREATE TABLE budgets (
    id                    UUID PRIMARY KEY,
    order_id              VARCHAR(64) NOT NULL,
    correlation_id        UUID NOT NULL,
    customer_document     VARCHAR(20),
    total_labors_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_supply_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_taxes_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_budget_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    iss_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    icms_amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
    status                VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','EXPIRED')),
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);
-- UNIQUE, não só índice: um orçamento por OS. Sem isso, duas mensagens eventGeneratePayment
-- pro mesmo serviceOrderId (retry, ou reprocessamento) criam linhas duplicadas e
-- findByOrderId() quebra com NonUniqueResultException.
CREATE UNIQUE INDEX idx_budgets_order_id ON budgets (order_id);

CREATE TABLE budget_items (
    id                 UUID PRIMARY KEY,
    budget_id          UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    external_item_id   VARCHAR(64) NOT NULL,
    item_type          VARCHAR(10) NOT NULL CHECK (item_type IN ('LABOR','SUPPLY')),
    description        VARCHAR(255) NOT NULL,
    amount             NUMERIC(12,2) NOT NULL,
    situation          VARCHAR(20) NOT NULL CHECK (situation IN ('PENDING_APPROVAL','APPROVED','REJECTED'))
);
CREATE INDEX idx_budget_items_budget_id ON budget_items (budget_id);

CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    budget_id             UUID NOT NULL REFERENCES budgets(id),
    order_id              VARCHAR(64) NOT NULL,
    external_payment_id   VARCHAR(64),
    payment_method        VARCHAR(20) NOT NULL CHECK (payment_method IN ('PIX','CREDIT_CARD','DEBIT_CARD')),
    qr_code               TEXT,
    qr_code_base64        TEXT,
    status                VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','WAITING_PAYMENT','PAID','FAILED')),
    paid_amount           NUMERIC(12,2),
    paid_at               TIMESTAMP
);
CREATE INDEX idx_payments_budget_id ON payments (budget_id);
CREATE INDEX idx_payments_external_payment_id ON payments (external_payment_id);
