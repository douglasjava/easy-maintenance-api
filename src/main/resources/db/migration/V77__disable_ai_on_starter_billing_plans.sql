UPDATE billing_plans
SET features_json = JSON_SET(features_json,
    '$.aiEnabled', false,
    '$.aiMonthlyCredits', 0
)
WHERE code IN ('STARTER', 'STARTER_ANNUAL');
