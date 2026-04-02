-- V7__create_sub_cards_table.sql
CREATE TABLE IF NOT EXISTS sub_cards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_method_id BIGINT NOT NULL,
    last_four_digits CHAR(4) NOT NULL,
    type VARCHAR(30) NOT NULL,
    nickname VARCHAR(100) DEFAULT NULL,
    dependent_name VARCHAR(100) DEFAULT NULL,
    wallet_platform VARCHAR(20) DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_cards_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
