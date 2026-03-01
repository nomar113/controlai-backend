-- V4__create_purchase_items_table.sql
CREATE TABLE IF NOT EXISTS purchase_items (
  id int NOT NULL AUTO_INCREMENT,
  product_name varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  code varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  quantity decimal(10,3) NOT NULL,
  unit varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  unit_price decimal(10,2) NOT NULL,
  total_price decimal(10,2) NOT NULL,
  purchase_invoice_id int NOT NULL,
  PRIMARY KEY (id),
  KEY item_purchase_invoice_id_fkey (purchase_invoice_id),
  CONSTRAINT item_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES purchase_invoices (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
