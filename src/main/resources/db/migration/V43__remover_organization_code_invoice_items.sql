ALTER TABLE invoice_items
    DROP FOREIGN KEY fk_invoice_items_org;

ALTER TABLE invoice_items
    DROP COLUMN organization_code;