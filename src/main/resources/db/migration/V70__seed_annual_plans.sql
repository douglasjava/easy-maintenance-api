-- Planos anuais com 17% de desconto (equivale a 2 meses grátis: mensal × 10)
-- STARTER_ANNUAL: R$1.490/ano | BUSINESS_ANNUAL: R$2.990/ano | ENTERPRISE_ANNUAL: R$8.990/ano
-- Features idênticas aos planos mensais equivalentes (pós-V63 + V69)

INSERT INTO billing_plans
(code, name, currency, billing_cycle, price_cents, features_json, status)
VALUES
    ('STARTER_ANNUAL',
     'Starter Anual',
     'BRL',
     'YEARLY',
     149000,
     '{
       "maxOrganizations": 3,
       "maxUsers": 3,
       "maxItems": 100,
       "aiEnabled": true,
       "aiMonthlyCredits": 5000,
       "emailMonthlyLimit": 500,
       "reportsEnabled": true,
       "supportLevel": "COMMUNITY",
       "maxFileSizeMb": 5,
       "maxMonthlyUploadsMb": 500
     }',
     'ACTIVE'),

    ('BUSINESS_ANNUAL',
     'Business Anual',
     'BRL',
     'YEARLY',
     299000,
     '{
       "maxOrganizations": 3,
       "maxUsers": 10,
       "maxItems": 500,
       "aiEnabled": true,
       "aiMonthlyCredits": 40000,
       "emailMonthlyLimit": 3000,
       "reportsEnabled": true,
       "supportLevel": "PRIORITY_EMAIL",
       "maxFileSizeMb": 20,
       "maxMonthlyUploadsMb": 2048
     }',
     'ACTIVE'),

    ('ENTERPRISE_ANNUAL',
     'Enterprise Anual',
     'BRL',
     'YEARLY',
     899000,
     '{
       "maxOrganizations": 10,
       "maxUsers": 100,
       "maxItems": 5000,
       "aiEnabled": true,
       "aiMonthlyCredits": 200000,
       "emailMonthlyLimit": 10000,
       "reportsEnabled": true,
       "supportLevel": "DEDICATED",
       "maxFileSizeMb": 50,
       "maxMonthlyUploadsMb": 10240
     }',
     'ACTIVE');
