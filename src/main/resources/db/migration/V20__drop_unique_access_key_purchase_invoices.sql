-- Permite recadastrar nota fiscal com mesma access_key quando a anterior foi excluída (soft delete).
-- A unicidade para registros ativos é garantida pela validação no SavePurchaseInvoiceProvider.
ALTER TABLE purchase_invoices DROP INDEX access_key;
