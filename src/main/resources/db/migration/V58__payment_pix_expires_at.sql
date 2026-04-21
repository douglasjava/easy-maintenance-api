-- TASK-046: add pix_expires_at to payments table
-- pix_qr_code and pix_qr_code_base64 already exist since V31.
-- This column stores the QR Code expiration date received from the Asaas
-- PAYMENT_CREATED webhook (pixTransaction.qrCode.expirationDate).
ALTER TABLE payments
    ADD COLUMN pix_expires_at TIMESTAMP NULL DEFAULT NULL;

CREATE INDEX idx_payments_pix_expires_at ON payments (pix_expires_at);
