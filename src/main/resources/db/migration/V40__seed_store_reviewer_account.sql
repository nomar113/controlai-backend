-- Seed a fixed account for the App Store / Google Play review teams to sign in with,
-- since public registration is disabled (see V39 grandfathering). The account gets its
-- own group, an ACTIVE subscription (so it is not blocked by SubscriptionGuardFilter)
-- and a small set of non-sensitive example data so reviewers do not land on empty screens.
-- Password hash below is a BCrypt(10) hash of a fixed secret shared out-of-band with the
-- product owner (see Tarefa 1.3 do PRD "Publicação nas Lojas") — never stored in plaintext.

INSERT INTO `groups` (name) VALUES ('ControlAI Revisor');
SET @reviewer_group_id = LAST_INSERT_ID();

INSERT INTO users (name, email, password_hash)
VALUES ('Revisor ControlAI', 'revisor.loja@nomar.com.br', '$2b$10$vhZTfD.1zIMGLi7VCCKvRuDVfkTz2UCeGFmLIV95rfAA5tx788sea');
SET @reviewer_user_id = LAST_INSERT_ID();

INSERT INTO group_members (group_id, user_id) VALUES (@reviewer_group_id, @reviewer_user_id);

-- Same 15 default categories seeded for every new group (see SeedDefaultCategoriesProvider).
INSERT INTO categories (group_id, name, icon) VALUES
(@reviewer_group_id, 'Alimentação', '🍽️'),
(@reviewer_group_id, 'Assinaturas', '📱'),
(@reviewer_group_id, 'Educação', '📚'),
(@reviewer_group_id, 'Emergência', '🚨'),
(@reviewer_group_id, 'Farmácia', '💊'),
(@reviewer_group_id, 'Lazer', '🎉'),
(@reviewer_group_id, 'Mercado', '🛒'),
(@reviewer_group_id, 'Moradia', '🏠'),
(@reviewer_group_id, 'Outros', '📦'),
(@reviewer_group_id, 'Pet', '🐾'),
(@reviewer_group_id, 'Restaurante', '🍴'),
(@reviewer_group_id, 'Roupas', '👕'),
(@reviewer_group_id, 'Saúde', '❤️'),
(@reviewer_group_id, 'Tecnologia', '💻'),
(@reviewer_group_id, 'Transporte', '🚗');

-- Active subscription so SubscriptionGuardFilter never blocks this account with a 402.
INSERT INTO subscriptions (group_id, plan, status)
VALUES (@reviewer_group_id, 'GRANDFATHERED', 'ACTIVE');

-- One fictitious card, so the reviewer sees a populated "Cartões" screen.
-- `holders.name` still carries a legacy GLOBAL unique constraint (not scoped by group_id,
-- see V5/V30) — use an upsert instead of a bare INSERT so an unlikely name collision reuses
-- the existing row instead of failing the whole migration (and blocking app startup for
-- every tenant, not just this seed).
INSERT INTO holders (group_id, name) VALUES (@reviewer_group_id, 'Revisor da Loja (App Review)')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);
SET @reviewer_holder_id = LAST_INSERT_ID();

INSERT INTO payment_methods (group_id, name, type, holder_id, brand)
VALUES (@reviewer_group_id, 'Cartão de Demonstração', 'CREDIT_CARD', @reviewer_holder_id, 'Visa');
SET @reviewer_payment_method_id = LAST_INSERT_ID();

-- Two fictitious manual purchases (no real financial data), so dashboard, faturas and
-- orçamento all have something to display instead of an empty state.
INSERT INTO payment_notifications (
  group_id, card_last_digits, purchased_at, amount, merchant_name,
  number_of_installments, origin, origin_type, category_id, payment_method_id, description
) VALUES (
  @reviewer_group_id, '4242', NOW() - INTERVAL 5 DAY, 89.90, 'Mercado Demonstração',
  1, 'MANUAL', 'MANUAL',
  (SELECT id FROM categories WHERE group_id = @reviewer_group_id AND name = 'Mercado'),
  @reviewer_payment_method_id, 'Compra de demonstração para revisão da loja'
);

INSERT INTO payment_notifications (
  group_id, card_last_digits, purchased_at, amount, merchant_name,
  number_of_installments, origin, origin_type, category_id, payment_method_id, description
) VALUES (
  @reviewer_group_id, '4242', NOW() - INTERVAL 2 DAY, 34.50, 'Restaurante Demonstração',
  1, 'MANUAL', 'MANUAL',
  (SELECT id FROM categories WHERE group_id = @reviewer_group_id AND name = 'Restaurante'),
  @reviewer_payment_method_id, 'Compra de demonstração para revisão da loja'
);
