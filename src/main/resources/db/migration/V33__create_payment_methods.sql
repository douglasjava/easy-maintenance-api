CREATE TABLE payment_methods
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,

    user_id     BIGINT       NOT NULL,

    -- CARD | PIX | BOLETO
    method_type VARCHAR(20)  NOT NULL,

    -- MERCADOPAGO | STRIPE | PAYPAL (futuro)
    provider    VARCHAR(40)  NOT NULL,

    -- ID externo retornado pelo gateway (customer/card token)
    external_id VARCHAR(160) NOT NULL,

    -- ACTIVE | INACTIVE
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- true = método principal
    is_default  BOOLEAN      NOT NULL DEFAULT false,

    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_payment_method_external (provider, external_id),

    KEY         idx_payment_method_user(user_id),

    CONSTRAINT fk_payment_method_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
