-- EPIC-014/TASK-114: modelo de plano único por conta.
--
-- Itens billing_subscription_items com source_type='ORGANIZATION' criados antes da TASK-110 ainda
-- carregam value_cents > 0, inflando indevidamente o total_cents da assinatura (cobrança duplicada
-- USER + ORGANIZATION).
--
-- Nota: o desenho original desta task previa DELETE dos itens ORGANIZATION. Durante a implementação
-- da TASK-110/111/113, esses itens passaram a ser necessários para resolver a conta (BillingSubscription)
-- a partir do código da organização e para compor o pool de organizações usado no enforcement de
-- maxItems e em GET /organizations/{code}/subscription. Por isso, em vez de remover as linhas, esta
-- migration apenas zera o valor cobrável dos itens ORGANIZATION e recalcula total_cents de todas as
-- assinaturas, usando a mesma regra do BillingSubscriptionService.recalculateTotal() em runtime (soma
-- apenas itens não cancelados).

UPDATE billing_subscription_items
SET value_cents = 0
WHERE source_type = 'ORGANIZATION'
  AND value_cents <> 0;

UPDATE billing_subscriptions bs
SET total_cents = (
    SELECT COALESCE(SUM(bsi.value_cents), 0)
    FROM billing_subscription_items bsi
    WHERE bsi.billing_subscription_id = bs.id
      AND bsi.canceled_at IS NULL
);
