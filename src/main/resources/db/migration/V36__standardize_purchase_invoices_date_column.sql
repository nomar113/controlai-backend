-- Padroniza purchase_invoices.date de datetime (naive) para TIMESTAMP.
-- A sessao de conexao usada pelo Flyway ja forca time_zone=UTC (application.yml,
-- connectionTimeZone=UTC), entao os valores existentes (ja tratados como UTC pela
-- aplicacao) sao preservados sem deslocamento na conversao de tipo.
ALTER TABLE purchase_invoices MODIFY COLUMN date TIMESTAMP NULL DEFAULT NULL;
