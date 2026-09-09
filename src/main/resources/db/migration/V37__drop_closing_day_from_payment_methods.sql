-- V37__drop_closing_day_from_payment_methods.sql
-- closing_day is no longer used: every payment method's invoice period now defaults to the
-- plain calendar month, with per-month custom cycles set via budget_payment_periods instead.
ALTER TABLE payment_methods DROP COLUMN closing_day;
