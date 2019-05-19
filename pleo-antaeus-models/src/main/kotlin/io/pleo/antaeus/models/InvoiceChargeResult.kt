package io.pleo.antaeus.models

enum class InvoiceChargeResult {
    FAILED_CONCURRENT_PAYMENT,
    FAILED_REJECTED,
    UNKNOWN,
    PAID
}