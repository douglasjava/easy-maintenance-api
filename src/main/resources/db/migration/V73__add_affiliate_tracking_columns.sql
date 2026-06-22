ALTER TABLE landing_leads
    ADD COLUMN affiliate_code VARCHAR(8) NULL;

ALTER TABLE organizations
    ADD COLUMN referral_code VARCHAR(8) NULL;
