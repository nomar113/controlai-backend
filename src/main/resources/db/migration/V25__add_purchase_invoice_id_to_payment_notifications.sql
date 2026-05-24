ALTER TABLE payment_notifications ADD COLUMN purchase_invoice_id INT NULL;

ALTER TABLE payment_notifications ADD CONSTRAINT fk_pn_purchase_invoice
    FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id);

ALTER TABLE payment_notifications ADD CONSTRAINT uq_pn_purchase_invoice_id
    UNIQUE (purchase_invoice_id);
