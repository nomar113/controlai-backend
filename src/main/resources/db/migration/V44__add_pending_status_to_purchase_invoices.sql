-- Pending invoices: the QR Code scan only stores the NFC-e link; the invoice data is fetched later.
-- invoice_url grows to 1024 because the offline QR Code v3 carries a digital signature.
-- active_access_key holds the key only while the invoice is not soft deleted, so the unique index
-- below blocks duplicates per group (even under concurrent scans) and a deleted invoice frees its key.
ALTER TABLE purchase_invoices
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
  MODIFY invoice_url VARCHAR(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN active_access_key VARCHAR(44)
      GENERATED ALWAYS AS (IF(deleted_at IS NULL, access_key, NULL)) STORED;

-- Soft delete active duplicates (same group_id + access_key), keeping the lowest id, so the index can be created.
UPDATE purchase_invoices p
JOIN (
    SELECT group_id, access_key, MIN(id) AS keep_id
    FROM purchase_invoices
    WHERE deleted_at IS NULL AND access_key IS NOT NULL
    GROUP BY group_id, access_key
    HAVING COUNT(*) > 1
) d ON d.group_id = p.group_id AND d.access_key = p.access_key
SET p.deleted_at = CURRENT_TIMESTAMP
WHERE p.deleted_at IS NULL AND p.id <> d.keep_id;

ALTER TABLE purchase_invoices
  ADD UNIQUE KEY uk_purchase_invoices_group_active_key (group_id, active_access_key);
