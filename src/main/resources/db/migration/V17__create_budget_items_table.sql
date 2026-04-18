-- V17__create_budget_items_table.sql
CREATE TABLE budget_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    type VARCHAR(15) NOT NULL,
    expected DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bi_budget FOREIGN KEY (budget_id) REFERENCES budgets (id) ON DELETE CASCADE,
    CONSTRAINT fk_bi_category FOREIGN KEY (category_id) REFERENCES categories (id),
    UNIQUE KEY uk_bi_budget_category (budget_id, category_id)
);
