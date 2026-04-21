-- V56: Performance indexes for high-frequency query patterns
-- Each index is documented with the repository query that motivated it.

-- ──────────────────────────────────────────────────────────────────────────────
-- maintenance_items
-- ──────────────────────────────────────────────────────────────────────────────

-- Composite (organization_code, next_due_at): covers the dashboard queries
--   findUpcoming:    WHERE organization_code = :org AND next_due_at BETWEEN :start AND :end
--   countDueBetween: same predicate
--   countDueSoon:    same predicate
-- The existing idx_items_org_status covers org+status; idx_items_next_due is
-- single-column only. This composite avoids a full org-table scan for date ranges.
CREATE INDEX idx_items_org_next_due
    ON maintenance_items (organization_code, next_due_at);

-- ──────────────────────────────────────────────────────────────────────────────
-- billing_subscription_items
-- V39 created this table with NO secondary indexes.
-- ──────────────────────────────────────────────────────────────────────────────

-- (billing_subscription_id): FK lookup — used by every query that loads items
-- for a subscription:
--   findAllByBillingSubscriptionId
--   findAllByBillingSubscriptionIdFetchPlan
--   findAllByBillingSubscriptionIdIn
CREATE INDEX idx_bsi_subscription
    ON billing_subscription_items (billing_subscription_id);

-- (source_type, source_id): used for org/user subscription resolution:
--   findBySourceId
--   findAllBySourceTypeAndSourceIdIn  (source_type = :type AND source_id IN :ids)
CREATE INDEX idx_bsi_source
    ON billing_subscription_items (source_type, source_id);

-- (plan_change_effective_at): scheduled job query:
--   findEligibleForPlanChange: WHERE plan_change_effective_at IS NOT NULL
--                              AND plan_change_effective_at <= :now
CREATE INDEX idx_bsi_plan_change_effective
    ON billing_subscription_items (plan_change_effective_at);
