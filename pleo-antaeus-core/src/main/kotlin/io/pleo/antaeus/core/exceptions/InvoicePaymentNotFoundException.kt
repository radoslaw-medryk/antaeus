package io.pleo.antaeus.core.exceptions

class InvoicePaymentNotFoundException(invoiceId: Int) : EntityNotFoundException("InvoicePayment", invoiceId)
