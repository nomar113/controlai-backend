-- Step 3 of 3: enforce NOT NULL, add FK constraints and composite indexes.
-- At this point every row has group_id filled in by V29.

-- ── holders ──────────────────────────────────────────────────────────────────
-- Holders do not have a global unique name constraint to drop.
ALTER TABLE holders
    MODIFY group_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_holders_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD INDEX idx_holders_group (group_id);

-- ── payment_methods ───────────────────────────────────────────────────────────
ALTER TABLE payment_methods
    MODIFY group_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_payment_methods_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD INDEX idx_payment_methods_group (group_id);

-- ── categories ────────────────────────────────────────────────────────────────
-- Replace the global unique name constraint with a per-group composite one.
ALTER TABLE categories
    MODIFY group_id BIGINT NOT NULL,
    DROP INDEX uk_categories_name,
    ADD CONSTRAINT fk_categories_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD CONSTRAINT uk_categories_group_name UNIQUE (group_id, name);

-- ── budgets ───────────────────────────────────────────────────────────────────
-- Replace the global unique reference_month constraint with a per-group one.
ALTER TABLE budgets
    MODIFY group_id BIGINT NOT NULL,
    DROP INDEX uk_budgets_reference_month,
    ADD CONSTRAINT fk_budgets_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD CONSTRAINT uk_budgets_group_month UNIQUE (group_id, reference_month);

-- ── payment_notifications ─────────────────────────────────────────────────────
ALTER TABLE payment_notifications
    MODIFY group_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_payment_notifications_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD INDEX idx_payment_notifications_group_created (group_id, created_at);

-- ── purchase_invoices ─────────────────────────────────────────────────────────
ALTER TABLE purchase_invoices
    MODIFY group_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_purchase_invoices_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD INDEX idx_purchase_invoices_group_date (group_id, date);

-- ── installments ──────────────────────────────────────────────────────────────
ALTER TABLE installments
    MODIFY group_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_installments_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    ADD INDEX idx_installments_group (group_id);
