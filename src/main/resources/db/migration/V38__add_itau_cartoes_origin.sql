-- V38__add_itau_cartoes_origin.sql
ALTER TABLE payment_notifications
    MODIFY COLUMN origin ENUM('BRADESCO_CARTOES', 'NUBANK', 'MANUAL', 'ITAU_CARTOES') NOT NULL;
