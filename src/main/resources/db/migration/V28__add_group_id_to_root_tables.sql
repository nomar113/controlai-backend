-- Step 1 of 3: add nullable group_id to all root data tables.
-- Child tables (sub_cards, purchase_items, purchase_payments,
-- budget_items, budget_incomes, budget_payment_periods)
-- inherit tenancy via FK from their parent — no column needed there.

ALTER TABLE holders            ADD COLUMN group_id BIGINT NULL AFTER id;
ALTER TABLE payment_methods    ADD COLUMN group_id BIGINT NULL AFTER id;
ALTER TABLE categories         ADD COLUMN group_id BIGINT NULL AFTER id;
ALTER TABLE budgets            ADD COLUMN group_id BIGINT NULL AFTER id;
ALTER TABLE payment_notifications ADD COLUMN group_id BIGINT NULL AFTER id;
ALTER TABLE purchase_invoices  ADD COLUMN group_id BIGINT NULL AFTER id;
ALTER TABLE installments       ADD COLUMN group_id BIGINT NULL AFTER id;
