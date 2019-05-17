/*
    Defines database tables and their schemas.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import org.jetbrains.exposed.sql.Table

object InvoiceTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
    val value = decimal("value", 1000, 2)
    val customerId = reference("customer_id", CustomerTable.id)
    val status = text("status")
}

object InvoicePaymentTable : Table() {
    // invoideId is bot FK and PK - this ensures only one entry can exist for single invoice
    val invoiceId = reference("invoice_id", InvoiceTable.id).primaryKey()
    val status = text("status")
}

object CustomerTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
}
