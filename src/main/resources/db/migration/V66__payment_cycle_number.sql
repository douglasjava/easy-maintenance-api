-- TASK-059: Idempotência por ciclo para PIX recorrente "manual"
ALTER TABLE payments
    ADD COLUMN cycle_number INT NULL AFTER billing_subscription_id;

-- Backfill: numera sequencialmente os pagamentos existentes por subscription,
-- ordenando pela data de criação para preservar a sequência histórica.
UPDATE payments p
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY billing_subscription_id
               ORDER BY created_at, id
           ) AS rn
    FROM payments
    WHERE billing_subscription_id IS NOT NULL
) ranked ON p.id = ranked.id
SET p.cycle_number = ranked.rn;

-- Garante idempotência entre execuções paralelas do PixRenewalJob.
-- MySQL/InnoDB permite múltiplos NULL em UNIQUE, então pagamentos sem
-- billing_subscription_id (legado) seguem aceitos.
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_subscription_cycle
        UNIQUE (billing_subscription_id, cycle_number);
