/*
    Implements the data access layer (DAL).
    This file implements the database queries used to fetch and insert rows in our database tables.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    fun fetchInvoicePayment(invoice: Invoice): InvoicePayment? {
        return transaction(db) {
            InvoicePaymentTable
                .select { InvoicePaymentTable.invoiceId.eq(invoice.id) }
                .firstOrNull()
                ?.toInvoicePayment()
        }
    }

    fun fetchInvoicePayments(): List<InvoicePayment> {
        return transaction(db) {
            InvoicePaymentTable
                .selectAll()
                .map { it.toInvoicePayment() }
        }
    }

    fun createInvoicePayment(invoice: Invoice, status: InvoicePaymentStatus = InvoicePaymentStatus.STARTED): InvoicePayment? {
        transaction(db) {
            InvoicePaymentTable
                .insert {
                    it[this.invoiceId] = invoice.id
                    it[this.status] = status.toString()
                } get InvoicePaymentTable.invoiceId
        }
        // TODO [RM]: up /\ will throw when InvoicePayment already exist for this invoice.
        // TODO [RM]: catch specific exception and wrap into nicer one.

        return fetchInvoicePayment(invoice)
    }

    fun updateInvoicePaymentStatus(invoicePayment: InvoicePayment, status: InvoicePaymentStatus): Boolean {
        val updatedCount = transaction(db) {
            InvoicePaymentTable
                .update {
                    it[this.invoiceId] = invoicePayment.invoiceId
                    it[this.status] = status.toString()
                }
        }

        return updatedCount > 0
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id!!)
    }
}
