package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceChargeResult
import io.pleo.antaeus.models.InvoicePaymentStatus

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {
    fun chargeInvoice(invoiceId: Int): InvoiceChargeResult {
        val invoice = dal.fetchInvoice(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)

        val isPaymentStarted = dal.markInvoicePaymentStarted(invoiceId)
        if (!isPaymentStarted) {
            // markInvoicePaymentStarted returns false if another payment is in progress/paid already.
            return InvoiceChargeResult.FAILED_CONCURRENT_PAYMENT
        }

        try {
            val isPaymentSucceeded = paymentProvider.charge(invoice)

            return if (isPaymentSucceeded) {
                dal.markInvoicePaymentPaid(invoiceId)
                InvoiceChargeResult.PAID
            } else {
                dal.markInvoicePaymentFailed(invoiceId)
                InvoiceChargeResult.FAILED_REJECTED
            }
        } catch (e: Throwable) {
            // We are unable to tell if payment was successfully processed by paymentProvider here.
            // It is possible that consumer was charged, but network error occured before we got notified.
            // We don't mark InvoicePayment as neither PAID or FAILED. It must be manually reviewed.
            return InvoiceChargeResult.UNKNOWN
        }
    }
}