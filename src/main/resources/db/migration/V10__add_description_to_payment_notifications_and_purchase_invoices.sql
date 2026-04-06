ALTER TABLE payment_notifications ADD COLUMN description VARCHAR(255) DEFAULT NULL;
ALTER TABLE purchase_invoices ADD COLUMN description VARCHAR(255) DEFAULT NULL;
