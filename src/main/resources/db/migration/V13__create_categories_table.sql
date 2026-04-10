-- V13__create_categories_table.sql
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO categories (name) VALUES
('Gastos Gerais'), ('Mercado'), ('Veiculos'), ('Pets'), ('Moradia'),
('Remedios'), ('Medicos'), ('Transporte'), ('Viagens'), ('Assinaturas'),
('Lazer'), ('Educacao'), ('Vestuario'), ('Beleza'), ('Presentes');
