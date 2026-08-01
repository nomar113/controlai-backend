-- Step 2 of 3: seed the legacy group, the Ramon user and backfill all existing rows.

-- Create the "Família" group (legacy id=1).
INSERT INTO `groups` (id, name) VALUES (1, 'Família');

-- Create Ramon's user record without a password hash
-- (he will define a password via "forgot password" or use Google Sign-In).
INSERT INTO users (id, name, email) VALUES (1, 'Ramon', 'ramonmesquita113@gmail.com');

-- Link Ramon to the "Família" group.
INSERT INTO group_members (group_id, user_id) VALUES (1, 1);

-- Backfill all existing data rows to the legacy group.
UPDATE holders              SET group_id = 1 WHERE group_id IS NULL;
UPDATE payment_methods      SET group_id = 1 WHERE group_id IS NULL;
UPDATE categories           SET group_id = 1 WHERE group_id IS NULL;
UPDATE budgets              SET group_id = 1 WHERE group_id IS NULL;
UPDATE payment_notifications SET group_id = 1 WHERE group_id IS NULL;
UPDATE purchase_invoices    SET group_id = 1 WHERE group_id IS NULL;
UPDATE installments         SET group_id = 1 WHERE group_id IS NULL;

-- Seed the 15 default categories for the legacy group.
-- New groups receive a copy of these on creation (see SeedDefaultCategoriesProvider).
INSERT INTO categories (group_id, name, icon) VALUES
(1, 'Alimentação',    '🍽️'),
(1, 'Assinaturas',    '📱'),
(1, 'Educação',       '📚'),
(1, 'Emergência',     '🚨'),
(1, 'Farmácia',       '💊'),
(1, 'Lazer',          '🎉'),
(1, 'Mercado',        '🛒'),
(1, 'Moradia',        '🏠'),
(1, 'Outros',         '📦'),
(1, 'Pet',            '🐾'),
(1, 'Restaurante',    '🍴'),
(1, 'Roupas',         '👕'),
(1, 'Saúde',          '❤️'),
(1, 'Tecnologia',     '💻'),
(1, 'Transporte',     '🚗')
ON DUPLICATE KEY UPDATE group_id = group_id;
