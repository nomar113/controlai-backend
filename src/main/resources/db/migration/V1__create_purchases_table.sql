-- V1__create_purchases_table.sql
CREATE TABLE IF NOT EXISTS purchases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_last_digits CHAR(4) NOT NULL,
  purchased_at TIMESTAMP NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  merchant_name VARCHAR(255) NOT NULL
);
