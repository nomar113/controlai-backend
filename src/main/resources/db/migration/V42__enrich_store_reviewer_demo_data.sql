-- Enrich the App Store / Google Play reviewer account (V40) so the features that set ControlAI
-- apart are visible right after sign-in: an NFC-e receipt with its line items, cards grouped by
-- two holders (the couple), an installment purchase spread across future invoices and a monthly
-- budget by category. Dates are relative to when this migration runs, clamped to the current
-- month so the demo purchases always land in the month the reviewer opens first.
--
-- Every insert is guarded (reviewer group must exist, rows must not exist yet) so this can never
-- fail and block app startup, even if the reviewer already created data while using the app.
--
-- String columns of the derived tables below carry the connection collation, so comparisons
-- against table columns force the tables' utf8mb4_unicode_ci explicitly.
--
-- No sub_cards are created on purpose: SavePaymentNotificationProvider resolves a card by its
-- last four digits across ALL groups, so a demo sub-card could capture real users' purchases.

SET @reviewer_group_id = (
    SELECT gm.group_id
    FROM group_members gm
    JOIN users u ON u.id = gm.user_id
    WHERE u.email = 'revisor.loja@nomar.com.br'
    LIMIT 1
);

SET @reviewer_holder_id = (
    SELECT id FROM holders WHERE group_id = @reviewer_group_id ORDER BY id LIMIT 1
);
SET @reviewer_card_id = (
    SELECT id FROM payment_methods
    WHERE group_id = @reviewer_group_id AND name = 'Cartão de Demonstração'
    LIMIT 1
);

SET @month_start = CAST(DATE_FORMAT(NOW(), '%Y-%m-01') AS DATETIME);

-- ── Second holder (the partner) with its own card, plus a Pix method ─────────────────────────
INSERT INTO holders (group_id, name)
SELECT @reviewer_group_id, 'Parceira Demonstração'
FROM DUAL
WHERE @reviewer_group_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM holders WHERE group_id = @reviewer_group_id AND name = 'Parceira Demonstração'
  );
SET @partner_holder_id = (
    SELECT id FROM holders WHERE group_id = @reviewer_group_id AND name = 'Parceira Demonstração' LIMIT 1
);

INSERT INTO payment_methods (group_id, name, type, holder_id, brand)
SELECT @reviewer_group_id, 'Cartão da Parceira', 'CREDIT_CARD', @partner_holder_id, 'Mastercard'
FROM DUAL
WHERE @partner_holder_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM payment_methods WHERE group_id = @reviewer_group_id AND name = 'Cartão da Parceira'
  );
SET @partner_card_id = (
    SELECT id FROM payment_methods WHERE group_id = @reviewer_group_id AND name = 'Cartão da Parceira' LIMIT 1
);

INSERT INTO payment_methods (group_id, name, type, holder_id, brand)
SELECT @reviewer_group_id, 'Pix', 'PIX', @reviewer_holder_id, NULL
FROM DUAL
WHERE @reviewer_holder_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM payment_methods WHERE group_id = @reviewer_group_id AND name = 'Pix'
  );
SET @pix_id = (
    SELECT id FROM payment_methods WHERE group_id = @reviewer_group_id AND name = 'Pix' LIMIT 1
);

-- ── NFC-e receipt with line items, linked to a card purchase (same shape as a scanned one) ────
-- The category lives on the purchase (what Home and the budget read), not on the receipt.
SET @nfce_at = GREATEST(@month_start, NOW() - INTERVAL 1 DAY);

INSERT INTO purchase_invoices (
    group_id, date, merchant_name, merchant_address, cnpj, total_items, access_key,
    subtotal, total, taxes, discount, description
)
SELECT
    @reviewer_group_id, @nfce_at, 'Supermercado Demonstração', 'Rua de Exemplo, 100 - Rio de Janeiro/RJ',
    '00.000.000/0001-00', 9, '33000000000000000000650010000000421000000420',
    250.42, 250.42, 41.30, 0.00,
    'Nota fiscal de demonstração para revisão da loja'
FROM DUAL
WHERE @reviewer_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM purchase_invoices WHERE access_key = '33000000000000000000650010000000421000000420');
SET @nfce_invoice_id = (
    SELECT id FROM purchase_invoices
    WHERE group_id = @reviewer_group_id AND access_key = '33000000000000000000650010000000421000000420'
    LIMIT 1
);

INSERT INTO purchase_items (product_name, code, quantity, unit, unit_price, total_price, purchase_invoice_id)
SELECT i.product_name, i.code, i.quantity, i.unit, i.unit_price, i.total_price, @nfce_invoice_id
FROM (
    SELECT 'ARROZ TIPO 1 5KG' AS product_name, '7890000000011' AS code, 1.000 AS quantity, 'UN' AS unit, 27.90 AS unit_price, 27.90 AS total_price
    UNION ALL SELECT 'FEIJAO CARIOCA 1KG', '7890000000028', 2.000, 'UN', 8.49, 16.98
    UNION ALL SELECT 'AZEITE EXTRA VIRGEM 500ML', '7890000000035', 1.000, 'UN', 34.90, 34.90
    UNION ALL SELECT 'CAFE TORRADO E MOIDO 500G', '7890000000042', 2.000, 'UN', 18.90, 37.80
    UNION ALL SELECT 'LEITE INTEGRAL 1L', '7890000000059', 12.000, 'UN', 5.29, 63.48
    UNION ALL SELECT 'BANANA PRATA', '2000000000017', 1.235, 'KG', 6.99, 8.63
    UNION ALL SELECT 'PEITO DE FRANGO', '2000000000024', 1.850, 'KG', 22.90, 42.37
    UNION ALL SELECT 'DETERGENTE LIQUIDO 500ML', '7890000000066', 3.000, 'UN', 2.79, 8.37
    UNION ALL SELECT 'PAO DE FORMA', '7890000000073', 1.000, 'UN', 9.99, 9.99
) i
WHERE @nfce_invoice_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM purchase_items WHERE purchase_invoice_id = @nfce_invoice_id);

-- ── Demo purchases this month ─────────────────────────────────────────────────────────────────
INSERT INTO payment_notifications (
    group_id, card_last_digits, purchased_at, amount, merchant_name, number_of_installments,
    origin, origin_type, category_id, payment_method_id, purchase_invoice_id, description
)
SELECT
    @reviewer_group_id, p.card_last_digits, p.purchased_at, p.amount, p.merchant_name, p.number_of_installments,
    'MANUAL', 'MANUAL',
    (SELECT id FROM categories WHERE group_id = @reviewer_group_id AND name = p.category_name COLLATE utf8mb4_unicode_ci),
    p.payment_method_id, p.purchase_invoice_id, 'Compra de demonstração para revisão da loja'
FROM (
    SELECT '4242' AS card_last_digits, @nfce_at AS purchased_at, 250.42 AS amount,
           'Supermercado Demonstração' AS merchant_name, 1 AS number_of_installments,
           'Mercado' AS category_name, @reviewer_card_id AS payment_method_id, @nfce_invoice_id AS purchase_invoice_id
    UNION ALL SELECT '5555', GREATEST(@month_start, NOW() - INTERVAL 3 DAY), 1199.40,
           'Loja de Eletrônicos Demonstração', 6, 'Tecnologia', @partner_card_id, NULL
    UNION ALL SELECT '5555', GREATEST(@month_start, NOW() - INTERVAL 2 DAY), 126.80,
           'Pizzaria Demonstração', 1, 'Restaurante', @partner_card_id, NULL
    UNION ALL SELECT '4242', GREATEST(@month_start, NOW() - INTERVAL 4 DAY), 58.70,
           'Farmácia Demonstração', 1, 'Farmácia', @reviewer_card_id, NULL
    UNION ALL SELECT NULL, GREATEST(@month_start, NOW() - INTERVAL 1 DAY), 32.50,
           'Estacionamento Demonstração', 1, 'Transporte', @pix_id, NULL
) p
WHERE @reviewer_group_id IS NOT NULL
  AND p.payment_method_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM payment_notifications pn
      WHERE pn.group_id = @reviewer_group_id AND pn.merchant_name = p.merchant_name COLLATE utf8mb4_unicode_ci
  );

-- Installments exactly as CreateInstallmentsProvider generates them with the default calendar
-- invoice cycle: remainder on the first one, due on the purchase day of each following month.
INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date)
SELECT
    pn.group_id, pn.id, n.num, pn.number_of_installments,
    ROUND(TRUNCATE(pn.amount / pn.number_of_installments, 2)
          + IF(n.num = 1, pn.amount - TRUNCATE(pn.amount / pn.number_of_installments, 2) * pn.number_of_installments, 0), 2),
    DATE(pn.purchased_at) + INTERVAL (n.num - 1) MONTH
FROM payment_notifications pn
JOIN (
    SELECT 1 AS num UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
) n ON n.num <= pn.number_of_installments
WHERE pn.group_id = @reviewer_group_id
  AND pn.description = 'Compra de demonstração para revisão da loja'
  AND NOT EXISTS (SELECT 1 FROM installments i WHERE i.parent_id = pn.id);

-- ── Monthly budget by category, for the current month and every month an installment lands in
-- (EnsureFutureBudgetProvider would create these copying the latest budget's items/incomes) ───
INSERT INTO budgets (group_id, reference_month)
SELECT @reviewer_group_id, DATE_FORMAT(@month_start + INTERVAL m.month_offset MONTH, '%Y-%m')
FROM (
    SELECT 0 AS month_offset UNION ALL SELECT 1 UNION ALL SELECT 2
    UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) m
WHERE @reviewer_group_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM budgets b
      WHERE b.group_id = @reviewer_group_id
        AND b.reference_month = DATE_FORMAT(@month_start + INTERVAL m.month_offset MONTH, '%Y-%m')
  );

INSERT INTO budget_items (budget_id, category_id, type, expected)
SELECT b.id, c.id, 'EXPENSE', x.expected
FROM budgets b
JOIN (
    SELECT 'Moradia' AS name, 2200.00 AS expected
    UNION ALL SELECT 'Mercado', 1200.00
    UNION ALL SELECT 'Restaurante', 450.00
    UNION ALL SELECT 'Transporte', 400.00
    UNION ALL SELECT 'Tecnologia', 300.00
    UNION ALL SELECT 'Lazer', 300.00
    UNION ALL SELECT 'Farmácia', 200.00
) x
JOIN categories c ON c.group_id = @reviewer_group_id AND c.name = x.name COLLATE utf8mb4_unicode_ci
WHERE b.group_id = @reviewer_group_id
  AND b.reference_month BETWEEN DATE_FORMAT(@month_start, '%Y-%m')
                            AND DATE_FORMAT(@month_start + INTERVAL 5 MONTH, '%Y-%m')
  AND NOT EXISTS (SELECT 1 FROM budget_items bi WHERE bi.budget_id = b.id AND bi.category_id = c.id);

INSERT INTO budget_incomes (budget_id, label, amount)
SELECT b.id, x.label, x.amount
FROM budgets b
JOIN (
    SELECT 'Salário - Revisor' AS label, 6500.00 AS amount
    UNION ALL SELECT 'Salário - Parceira', 5200.00
) x
WHERE b.group_id = @reviewer_group_id
  AND b.reference_month BETWEEN DATE_FORMAT(@month_start, '%Y-%m')
                            AND DATE_FORMAT(@month_start + INTERVAL 5 MONTH, '%Y-%m')
  AND NOT EXISTS (SELECT 1 FROM budget_incomes bi WHERE bi.budget_id = b.id AND bi.label = x.label COLLATE utf8mb4_unicode_ci);

-- Default calendar-month invoice period per payment method (BudgetPeriodCalculator.calculateDates).
INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date)
SELECT
    b.id, pm.id,
    STR_TO_DATE(CONCAT(b.reference_month, '-01'), '%Y-%m-%d'),
    LAST_DAY(STR_TO_DATE(CONCAT(b.reference_month, '-01'), '%Y-%m-%d'))
FROM budgets b
JOIN payment_methods pm ON pm.group_id = b.group_id
WHERE b.group_id = @reviewer_group_id
  AND b.reference_month BETWEEN DATE_FORMAT(@month_start, '%Y-%m')
                            AND DATE_FORMAT(@month_start + INTERVAL 5 MONTH, '%Y-%m')
  AND NOT EXISTS (
      SELECT 1 FROM budget_payment_periods bpp
      WHERE bpp.budget_id = b.id AND bpp.payment_method_id = pm.id
  );
