package br.com.nomar.controlai.domain.purchases_invoices.exception

class DuplicateInvoiceException(message: String = "Esta nota já foi registrada") : RuntimeException(message)
