-- V2__create_purchases_invoices_table.sql
CREATE TABLE IF NOT EXISTS purchase_invoices (
    id int NOT NULL AUTO_INCREMENT,
    date datetime DEFAULT NULL,
    merchant_address varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    merchant_name varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    cnpj varchar(18) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    total_items int DEFAULT NULL,
    invoice_url varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    access_key varchar(44) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    subtotal decimal(10,2) DEFAULT NULL,
    total decimal(10,2) DEFAULT NULL,
    taxes decimal(10,2) DEFAULT NULL,
    discount decimal(10,2) DEFAULT NULL,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;