INSERT INTO billing_plans
(code, name, currency, billing_cycle, price_cents, features_json, status)
VALUES
    ('FREE',
     'Free',
     'BRL',
     'MONTHLY',
     0,
     '{
       "maxOrganizations": 1,
       "maxUsers": 1,
       "maxItems": 30,
       "aiEnabled": false,
       "aiMonthlyCredits": 0,
       "emailMonthlyLimit": 100,
       "reportsEnabled": false,
       "supportLevel": "COMMUNITY"
     }',
     'ACTIVE'),

    ('STARTER',
     'Starter',
     'BRL',
     'MONTHLY',
     9900,
     '{
       "maxOrganizations": 2,
       "maxUsers": 5,
       "maxItems": 150,
       "aiEnabled": true,
       "aiMonthlyCredits": 20000,
       "emailMonthlyLimit": 1000,
       "reportsEnabled": true,
       "supportLevel": "EMAIL"
     }',
     'ACTIVE'),

    ('BUSINESS',
     'Business',
     'BRL',
     'MONTHLY',
     19900,
     '{
       "maxOrganizations": 5,
       "maxUsers": 15,
       "maxItems": 500,
       "aiEnabled": true,
       "aiMonthlyCredits": 60000,
       "emailMonthlyLimit": 3000,
       "reportsEnabled": true,
       "supportLevel": "PRIORITY_EMAIL"
     }',
     'ACTIVE'),

    ('ENTERPRISE',
     'Enterprise',
     'BRL',
     'MONTHLY',
     49900,
     '{
       "maxOrganizations": 999,
       "maxUsers": 100,
       "maxItems": 5000,
       "aiEnabled": true,
       "aiMonthlyCredits": 200000,
       "emailMonthlyLimit": 10000,
       "reportsEnabled": true,
       "supportLevel": "DEDICATED"
     }',
     'ACTIVE')

    AS new
ON DUPLICATE KEY UPDATE
                     name = new.name,
                     currency = new.currency,
                     billing_cycle = new.billing_cycle,
                     price_cents = new.price_cents,
                     features_json = new.features_json,
                     status = new.status;