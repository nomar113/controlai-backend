-- V8__add_payment_method_fks_to_payment_notifications.sql
ALTER TABLE payment_notifications
    ADD COLUMN payment_method_id BIGINT DEFAULT NULL,
    ADD COLUMN sub_card_id BIGINT DEFAULT NULL;

ALTER TABLE payment_notifications
    ADD CONSTRAINT fk_pn_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id),
    ADD CONSTRAINT fk_pn_sub_card FOREIGN KEY (sub_card_id) REFERENCES sub_cards (id);

CREATE INDEX idx_pn_payment_method_id ON payment_notifications (payment_method_id);
CREATE INDEX idx_pn_sub_card_id ON payment_notifications (sub_card_id);
