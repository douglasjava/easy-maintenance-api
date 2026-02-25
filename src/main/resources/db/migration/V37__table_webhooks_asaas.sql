CREATE TABLE webhook_event
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,

    provider_event_id VARCHAR(200) NOT NULL, -- evt_37260be8159d4472b4458d3de13efc2d&15370
    event_type        VARCHAR(100)  NOT NULL, -- CHECKOUT_CREATED

    event_created_at  TIMESTAMP    NOT NULL, -- 2024-10-31 18:07:47
    received_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    status            VARCHAR(20)  NOT NULL, -- RECEIVED | PROCESSING | PROCESSED | ERROR

    payload           JSON        NOT NULL, -- salva o JSON completo (muito importante)

    error_message     TEXT,
    processed_at      TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_webhook_event UNIQUE (provider_event_id)
);