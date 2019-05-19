package io.pleo.antaeus.models

enum class InvoiceChargeResultStatus {
    FAILED_CONCURRENT_PAYMENT,
    FAILED_REJECTED,
    UNKNOWN,
    PAID
}