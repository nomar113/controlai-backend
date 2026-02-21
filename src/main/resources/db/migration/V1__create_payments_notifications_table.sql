-- V1__create_payments_notifications_table.sql
CREATE TABLE IF NOT EXISTS payments_notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  card_last_digits CHAR(4) NOT NULL,
  purchased_at TIMESTAMP NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  merchant_name VARCHAR(255) NOT NULL,
  number_of_installments INT NOT NULL,
  origin ENUM('BRADESCO_CARTOES', 'NUBANK') NOT NULL,
  origin_type ENUM('SMS', 'HTTP_REQUEST') DEFAULT 'HTTP_REQUEST' NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
