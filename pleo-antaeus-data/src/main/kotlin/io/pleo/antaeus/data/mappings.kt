/*
    Defines mappings between database rows and Kotlin objects.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.ResultRow

fun invoicePaymentStatusToInvoiceStatus(invoicePaymentStatus: InvoicePaymentStatus?): InvoiceStatus =
    when (invoicePaymentStatus) {
        null -> InvoiceStatus.PENDING
        InvoicePaymentStatus.STARTED -> InvoiceStatus.IN_PROGRESS
        InvoicePaymentStatus.PAID -> InvoiceStatus.PAID
    }

fun ResultRow.toInvoice(): Invoice {
    val invoicePaymentStatus = if (this[InvoicePaymentTable.status] !== null) {
        InvoicePaymentStatus.valueOf(this[InvoicePaymentTable.status])
    } else {
        null
    }

    val invoiceStatus = invoicePaymentStatusToInvoiceStatus(invoicePaymentStatus)

    return Invoice(
        id = this[InvoiceTable.id],
        amount = Money(
            value = this[InvoiceTable.value],
            currency = Currency.valueOf(this[InvoiceTable.currency])
        ),
        customerId = this[InvoiceTable.customerId],
        status = invoiceStatus
    )
}

fun ResultRow.toInvoicePayment(): InvoicePayment = InvoicePayment(
        invoiceId = this[InvoicePaymentTable.invoiceId],
        status = InvoicePaymentStatus.valueOf(this[InvoicePaymentTable.status])
)

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[CustomerTable.id],
    currency = Currency.valueOf(this[CustomerTable.currency])
)
