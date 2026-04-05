-- V9__add_manual_origin_and_category.sql
ALTER TABLE payment_notifications
    MODIFY COLUMN origin ENUM('BRADESCO_CARTOES', 'NUBANK', 'MANUAL') NOT NULL;

ALTER TABLE payment_notifications
    MODIFY COLUMN origin_type ENUM('SMS', 'HTTP_REQUEST', 'MANUAL') DEFAULT 'HTTP_REQUEST' NOT NULL;

ALTER TABLE payment_notifications
    ADD COLUMN category VARCHAR(50) DEFAULT NULL;
