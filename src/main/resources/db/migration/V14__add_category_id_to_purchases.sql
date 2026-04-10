-- V14__add_category_id_to_purchases.sql
ALTER TABLE payment_notifications
    ADD COLUMN category_id BIGINT NULL DEFAULT NULL,
    ADD CONSTRAINT fk_pn_category FOREIGN KEY (category_id) REFERENCES categories (id);

ALTER TABLE purchase_invoices
    ADD COLUMN category_id BIGINT NULL DEFAULT NULL,
    ADD CONSTRAINT fk_pi_category FOREIGN KEY (category_id) REFERENCES categories (id);
