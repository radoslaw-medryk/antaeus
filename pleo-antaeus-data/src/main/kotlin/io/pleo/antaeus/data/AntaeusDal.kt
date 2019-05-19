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
            (InvoiceTable leftJoin InvoicePaymentTable)
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            (InvoiceTable leftJoin InvoicePaymentTable)
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchPendingInvoices(): List<Invoice> {
        return transaction(db) {
            (InvoiceTable leftJoin InvoicePaymentTable)
                .select { InvoicePaymentTable.status.isNull() }
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customerId: Int): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.customerId] = customerId
                } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    fun fetchInvoicePayment(invoiceId: Int): InvoicePayment? {
        return transaction(db) {
            InvoicePaymentTable
                .select { InvoicePaymentTable.invoiceId.eq(invoiceId) }
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

    fun markInvoicePaymentStarted(invoiceId: Int): Boolean {
        // Only single InvoicePayment can be in progress at the same time (by SQL PK constraints).
        // Successfully creating InvoicePayment guarantees concurrency-safe payment.
        try {
            // It will throw when InvoicePayment already exist for this invoice.
            transaction(db) {
                InvoicePaymentTable
                    .insert {
                        it[this.invoiceId] = invoiceId
                        it[this.status] = InvoicePaymentStatus.STARTED.toString()
                    }
            }
        } catch (e: Throwable) {
            // TODO [RM]: catch only specific exception and return false, else rethrow
            return false
        }

        return true
    }

    fun markInvoicePaymentPaid(invoiceId: Int): Boolean {
        // When payment succeeded, InvoicePayment will stay with status PAID, so no new payments can start.
        val updatedCount = transaction(db) {
            InvoicePaymentTable
                .update({
                    InvoicePaymentTable.invoiceId.eq(invoiceId) and
                    InvoicePaymentTable.status.eq(InvoicePaymentStatus.STARTED.toString())
                }) {
                    it[this.status] = InvoicePaymentStatus.PAID.toString()
                }
        }

        return updatedCount > 0
    }

    fun markInvoicePaymentFailed(invoiceId: Int): Boolean {
        // When payment failed, InvoicePayment is deleted to allow new payment retry to start.
        val deletedCount = transaction(db) {
            InvoicePaymentTable
                .deleteWhere {
                    InvoicePaymentTable.invoiceId.eq(invoiceId) and
                    InvoicePaymentTable.status.eq(InvoicePaymentStatus.STARTED.toString())
                }
        }

        return deletedCount > 0
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
