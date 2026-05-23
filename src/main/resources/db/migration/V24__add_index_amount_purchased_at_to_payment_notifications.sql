CREATE INDEX idx_pn_amount_purchased_at
    ON payment_notifications (amount, purchased_at);
