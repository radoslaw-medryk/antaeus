package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class BillingServiceTest {
    private val pendingInvoiceId = 1
    private val inProgressInvoiceId = 2
    private val paidInvoiceId = 3
    private val missingInvoiceId = 404

    private val amount = Money(value = BigDecimal(8.88), currency = Currency.USD)

    private val dal = mockk<AntaeusDal> {
        every { fetchInvoice(pendingInvoiceId) } returns Invoice(id = pendingInvoiceId, customerId = 1, amount = amount, status = InvoiceStatus.PENDING)
        every { fetchInvoice(inProgressInvoiceId) } returns Invoice(id = inProgressInvoiceId, customerId = 1, amount = amount, status = InvoiceStatus.IN_PROGRESS)
        every { fetchInvoice(paidInvoiceId) } returns Invoice(id = paidInvoiceId, customerId = 1, amount = amount, status = InvoiceStatus.PAID)
        every { fetchInvoice(missingInvoiceId) } returns null

        every { markInvoicePaymentStarted(pendingInvoiceId) } returns true
        every { markInvoicePaymentStarted(inProgressInvoiceId) } returns false
        every { markInvoicePaymentStarted(paidInvoiceId) } returns false
        every { markInvoicePaymentStarted(missingInvoiceId) } returns false

        every { markInvoicePaymentPaid(any()) } returns true
        every { markInvoicePaymentFailed(any()) } returns true
    }

    private val paymentProviderCharging = mockk<PaymentProvider>{
        every { charge(any()) } returns true
    }

    private val paymentProviderRejecting = mockk<PaymentProvider>{
        every { charge(any()) } returns false
    }

    private val paymentProviderThrowing = mockk<PaymentProvider>{
        every { charge(any()) } throws NetworkException()
    }

    @Test
    fun `will successfully charge when invoice pending`() {
        val billingService = BillingService(paymentProvider = paymentProviderCharging, dal = dal)

        val result = billingService.chargeInvoice(pendingInvoiceId)

        assert(
            result.status === InvoiceChargeResultStatus.PAID
        )
    }

    @Test
    fun `will fail (concurrent payment) when invoice in progress`() {
        val billingService = BillingService(paymentProvider = paymentProviderCharging, dal = dal)

        val result = billingService.chargeInvoice(inProgressInvoiceId)

        assert(
                result.status === InvoiceChargeResultStatus.FAILED_CONCURRENT_PAYMENT
        )
    }

    @Test
    fun `will fail (concurrent payment) when invoice paid`() {
        val billingService = BillingService(paymentProvider = paymentProviderCharging, dal = dal)

        val result = billingService.chargeInvoice(paidInvoiceId)

        assert(
                result.status === InvoiceChargeResultStatus.FAILED_CONCURRENT_PAYMENT
        )
    }

    @Test
    fun `will throw when invoice missing`() {
        val billingService = BillingService(paymentProvider = paymentProviderCharging, dal = dal)

        assertThrows<InvoiceNotFoundException> {
            billingService.chargeInvoice(missingInvoiceId)
        }
    }

    @Test
    fun `will fail (rejected) when invoice pending and paymentProvider rejects`() {
        val billingService = BillingService(paymentProvider = paymentProviderRejecting, dal = dal)

        val result = billingService.chargeInvoice(pendingInvoiceId)

        assert(
                result.status === InvoiceChargeResultStatus.FAILED_REJECTED
        )
    }

    @Test
    fun `will fail (concurrent payment) when invoice in progress and paymentProvider rejects`() {
        val billingService = BillingService(paymentProvider = paymentProviderRejecting, dal = dal)

        val result = billingService.chargeInvoice(inProgressInvoiceId)

        assert(
                result.status === InvoiceChargeResultStatus.FAILED_CONCURRENT_PAYMENT
        )
    }

    @Test
    fun `will block invoice (unknown) when invoice pending and paymentProvider throws`() {
        val billingService = BillingService(paymentProvider = paymentProviderThrowing, dal = dal)

        val result = billingService.chargeInvoice(pendingInvoiceId)

        assert(
                result.status === InvoiceChargeResultStatus.UNKNOWN
        )
    }

    @Test
    fun `will fail (concurrent payment) when invoice in progress and paymentProvider throws`() {
        val billingService = BillingService(paymentProvider = paymentProviderThrowing, dal = dal)

        val result = billingService.chargeInvoice(inProgressInvoiceId)

        assert(
                result.status === InvoiceChargeResultStatus.FAILED_CONCURRENT_PAYMENT
        )
    }
}