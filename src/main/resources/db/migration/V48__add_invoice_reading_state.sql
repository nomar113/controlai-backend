-- Reading state of pending invoices, filled by the NFC-e processor (prd-processamento-nfce-pendentes).
-- read_attempts/last_read_failure count failed reads until the invoice moves to READ_FAILED;
-- processed_at marks when a pending invoice became PROCESSED (drives the "N notas foram carregadas" notice).
-- The (status, created_at) index serves the processor's queue: PENDING invoices, oldest first.
ALTER TABLE purchase_invoices
  ADD COLUMN read_attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN last_read_failure VARCHAR(40) NULL,
  ADD COLUMN processed_at TIMESTAMP(6) NULL,
  ADD INDEX idx_purchase_invoices_status_created (status, created_at);

-- Last time the user saw (or dismissed) the notice. The column default fills it for every new user,
-- whatever the sign-up flow (e-mail, Google, Apple, seeds), so an invoice processed before the
-- account existed is never "new" to it.
ALTER TABLE users ADD COLUMN invoice_notice_seen_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6);

-- Old invoices are never "new"
UPDATE users SET invoice_notice_seen_at = NOW(6);

-- Invoices each user has opened since they were processed: the "Nova" badge goes away per user.
CREATE TABLE invoice_views (
  user_id BIGINT NOT NULL,
  purchase_invoice_id INT NOT NULL,
  viewed_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (user_id, purchase_invoice_id),
  CONSTRAINT fk_invoice_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_invoice_views_purchase_invoice FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices(id) ON DELETE CASCADE
);
