-- V3__create_purchase_payments_table.sql
CREATE TABLE IF NOT EXISTS purchase_payments (
  id int NOT NULL AUTO_INCREMENT,
  type varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  value decimal(10,2) NOT NULL,
  purchase_invoices_id int NOT NULL,
  PRIMARY KEY (id),
  KEY purchase_invoices_id_fkey (purchase_invoices_id),
  CONSTRAINT purchase_invoices_id_fkey FOREIGN KEY (purchase_invoices_id) REFERENCES purchase_invoices (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
