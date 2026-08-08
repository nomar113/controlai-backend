-- V35__drop_legacy_category_column_from_payment_notifications.sql
-- O nome da categoria deixou de ser persistido como coluna denormalizada.
-- category_id (com FK declarada via @ManyToOne em PaymentNotification) passa a ser
-- a unica fonte de verdade; o nome e sempre resolvido a partir dela na leitura.
ALTER TABLE payment_notifications DROP COLUMN category;
