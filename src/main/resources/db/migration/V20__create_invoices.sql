CREATE TABLE IF NOT EXISTS invoices
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,

    payer_user_id  BIGINT      NOT NULL,
    currency       CHAR(3)     NOT NULL DEFAULT 'BRL',

    period_start   DATE        NOT NULL,
    period_end     DATE        NOT NULL,

    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN|PAID|CANCELED
    due_date       DATE        NULL,

    subtotal_cents INT         NOT NULL DEFAULT 0,
    discount_cents INT         NOT NULL DEFAULT 0,
    total_cents    INT         NOT NULL DEFAULT 0,

    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_invoices_payer_period (payer_user_id, period_start, period_end),
    KEY            idx_invoices_status(status),
    KEY            idx_invoices_due_date(due_date),

    CONSTRAINT fk_invoices_payer
        FOREIGN KEY (payer_user_id) REFERENCES users (id)
            ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE IF NOT EXISTS invoice_items
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,

    invoice_id        BIGINT       NOT NULL,
    organization_code CHAR(36)     NOT NULL,

    plan_code         VARCHAR(40)  NOT NULL,
    description       VARCHAR(255) NOT NULL,
    quantity          INT          NOT NULL DEFAULT 1,
    unit_amount_cents INT          NOT NULL DEFAULT 0,
    amount_cents      INT          NOT NULL DEFAULT 0,

    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY               idx_invoice_items_invoice(invoice_id),
    KEY               idx_invoice_items_org(organization_code),
    KEY               idx_invoice_items_plan(plan_code),

    CONSTRAINT fk_invoice_items_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id)
            ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_invoice_items_org
        FOREIGN KEY (organization_code) REFERENCES organizations (code)
            ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_invoice_items_plan
        FOREIGN KEY (plan_code) REFERENCES billing_plans (code)
            ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
