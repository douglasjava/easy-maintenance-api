-- Reestruturação dos planos de billing
-- Remove plano FREE (desativa sem deletar para preservar integridade referencial)
-- Atualiza STARTER, BUSINESS e ENTERPRISE com nova grade de features
-- Adiciona campos: maxFileSizeMb, maxMonthlyUploadsMb

UPDATE billing_plans SET status = 'INACTIVE' WHERE code = 'FREE';

UPDATE billing_plans SET
    features_json = '{
      "maxOrganizations": 1,
      "maxUsers": 3,
      "maxItems": 100,
      "aiEnabled": true,
      "aiMonthlyCredits": 5000,
      "emailMonthlyLimit": 500,
      "reportsEnabled": true,
      "supportLevel": "COMMUNITY",
      "maxFileSizeMb": 5,
      "maxMonthlyUploadsMb": 500
    }'
WHERE code = 'STARTER';

UPDATE billing_plans SET
    features_json = '{
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
    }'
WHERE code = 'BUSINESS';

UPDATE billing_plans SET
    features_json = '{
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
    }'
WHERE code = 'ENTERPRISE';
