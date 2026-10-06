package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// max matches the invoice_url column (V44), so a longer QR Code is a 400 instead of a failed insert
data class RegisterPendingInvoiceRequest(
    @field:NotBlank
    @field:Size(max = 1024)
    val qrCodeContent: String,
)
