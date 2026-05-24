-- Align collation of string columns in payment_notifications with purchase_invoices (utf8mb4_unicode_ci)
-- This fixes "Illegal mix of collations" error in UNION queries between the two tables
ALTER TABLE payment_notifications
  MODIFY COLUMN merchant_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  MODIFY COLUMN category VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY COLUMN description VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL;
