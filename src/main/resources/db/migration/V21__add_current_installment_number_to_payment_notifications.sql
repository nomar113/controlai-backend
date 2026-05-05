ALTER TABLE payment_notifications
    ADD COLUMN current_installment_number INT NULL DEFAULT NULL AFTER number_of_installments;
