-- V15__create_installments_table.sql
CREATE TABLE IF NOT EXISTS installments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    total_installments INT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    due_date DATE NOT NULL,
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_installment_parent FOREIGN KEY (parent_id)
        REFERENCES payment_notifications (id),
    UNIQUE KEY uk_parent_number (parent_id, installment_number)
);
