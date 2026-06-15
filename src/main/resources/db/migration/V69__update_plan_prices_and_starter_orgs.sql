-- Ajuste de pricing baseado em análise de mercado (2026-06-15)
-- Sobe preços para refletir diferencial competitivo (IA inclusa vs concorrentes sem IA)
-- STARTER: R$99 → R$149 | BUSINESS: R$199 → R$299 | ENTERPRISE: R$499 → R$899
-- STARTER: maxOrganizations 1 → 3 (viabiliza administradoras de pequeno porte)

UPDATE billing_plans
SET price_cents = 14900
WHERE code = 'STARTER';

UPDATE billing_plans
SET price_cents = 29900
WHERE code = 'BUSINESS';

UPDATE billing_plans
SET price_cents = 89900
WHERE code = 'ENTERPRISE';

UPDATE billing_plans
SET features_json = JSON_SET(features_json, '$.maxOrganizations', 3)
WHERE code = 'STARTER';
