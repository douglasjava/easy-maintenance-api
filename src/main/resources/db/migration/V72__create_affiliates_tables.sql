CREATE TABLE affiliates (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    whatsapp        VARCHAR(20)  NOT NULL,
    code            VARCHAR(8)   NOT NULL,
    commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2000,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_affiliates_email (email),
    UNIQUE KEY uk_affiliates_code  (code)
);

CREATE TABLE referral_commissions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    affiliate_id      BIGINT        NOT NULL,
    organization_id   BIGINT        NOT NULL,
    plan_name         VARCHAR(100)  NOT NULL,
    plan_price        DECIMAL(10,2) NOT NULL,
    commission_rate   DECIMAL(5,4)  NOT NULL,
    commission_amount DECIMAL(10,2) NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    paid_at           DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_referral_commissions_org (organization_id),
    CONSTRAINT fk_rc_affiliate FOREIGN KEY (affiliate_id) REFERENCES affiliates (id)
);
