-- V__create_payments.sql

CREATE TABLE payments
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,

    -- relacionamento com invoice
    invoice_id          BIGINT      NOT NULL,

    -- quem pagou (facilita consultas e auditoria, mesmo já existindo no invoice)
    payer_user_id       BIGINT      NOT NULL,

    -- gateway
    provider            VARCHAR(40) NOT NULL,              -- MERCADO_PAGO | STRIPE | PAGARME | IUGU | ...
    method_type         VARCHAR(20) NOT NULL,              -- CARD | PIX | BOLETO
    status              VARCHAR(20) NOT NULL,              -- PENDING | PAID | FAILED | CANCELED | REFUNDED

-- valores
    amount_cents        INT         NOT NULL,
    currency            VARCHAR(8)  NOT NULL DEFAULT 'BRL',

    -- ids externos e dados de pagamento
    external_payment_id VARCHAR(120)         DEFAULT NULL,
    external_reference  VARCHAR(120)         DEFAULT NULL, -- opcional (ex: invoiceCode, correlationId)
    pix_qr_code         TEXT                 DEFAULT NULL, -- opcional (se você quiser armazenar)
    pix_qr_code_base64  MEDIUMTEXT           DEFAULT NULL, -- opcional (se você quiser armazenar)
    payment_link        VARCHAR(600)         DEFAULT NULL, -- opcional

-- erro / auditoria
    failure_reason      VARCHAR(240)         DEFAULT NULL,
    raw_payload_json    JSON                 DEFAULT NULL, -- opcional para webhook/debug

-- timestamps
    paid_at             TIMESTAMP   NULL     DEFAULT NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY                 idx_payments_invoice(invoice_id),
    KEY                 idx_payments_payer(payer_user_id),
    KEY                 idx_payments_status(status),
    KEY                 idx_payments_provider_ext(provider, external_payment_id),
    KEY                 idx_payments_created_at(created_at),

    CONSTRAINT fk_payments_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id)
            ON DELETE RESTRICT ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
