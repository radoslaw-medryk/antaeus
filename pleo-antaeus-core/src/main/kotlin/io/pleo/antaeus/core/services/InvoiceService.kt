/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.InvoicePaymentNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoicePayment

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
       return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchAllPayments(): List<InvoicePayment> {
        return dal.fetchInvoicePayments()
    }

    fun fetchPayment(invoiceId: Int): InvoicePayment {
        val invoice = dal.fetchInvoice(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)
        return dal.fetchInvoicePayment(invoice) ?: throw InvoicePaymentNotFoundException(invoiceId)
    }
}
