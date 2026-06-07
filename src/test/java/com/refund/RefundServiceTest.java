package com.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.refund.domain.Payment;
import com.refund.domain.RefundStatus;
import com.refund.domain.RiskLevel;
import com.refund.exception.ApiException;
import com.refund.repository.PaymentRepository;
import com.refund.service.ApprovalService;
import com.refund.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** One test per business rule / edge case. Runs against seeded payments; each test rolls back. */
@SpringBootTest
@Transactional
class RefundServiceTest {

    @Autowired
    RefundService refundService;
    @Autowired
    ApprovalService approvalService;
    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void partialRefund_onCapturedPayment_succeedsAndUpdatesBalance() {
        var refund = refundService.createRefund("pay_stripe_captured_100", 3000, "item returned", null);
        assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());
        Payment p = paymentRepository.findById("pay_stripe_captured_100").orElseThrow();
        assertEquals(3000, p.getRefundedAmountCents());
    }

    @Test
    void refund_onAuthorizedPayment_isRejected() {
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_adyen_authorized_300", 1000, null, null));
        assertEquals("PAYMENT_NOT_REFUNDABLE", ex.getCode());
    }

    @Test
    void refund_onFailedPayment_isRejected() {
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_stripe_failed_200", 1000, null, null));
        assertEquals("PAYMENT_NOT_REFUNDABLE", ex.getCode());
    }

    @Test
    void overRefund_isRejected() {
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_adyen_captured_50", 6000, null, null));
        assertEquals("REFUND_EXCEEDS_REMAINING", ex.getCode());
    }

    @Test
    void refund_onFullyRefundedPayment_isRejected() {
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_stripe_fully_refunded_400", 1000, null, null));
        assertEquals("ALREADY_FULLY_REFUNDED", ex.getCode());
    }

    @Test
    void legacyPay_partialRefund_isRejected() {
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_legacypay_captured_500", 10000, null, null));
        assertEquals("PARTIAL_REFUND_NOT_SUPPORTED", ex.getCode());
    }

    @Test
    void legacyPay_fullRefund_succeeds() {
        var refund = refundService.createRefund("pay_legacypay_captured_500", 50000, null, null);
        assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());
    }

    @Test
    void zeroAmount_isRejected() {
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_stripe_captured_100", 0, null, null));
        assertEquals("INVALID_AMOUNT", ex.getCode());
    }

    @Test
    void largeRefund_requiresApproval_thenApproveSettles() {
        var refund = refundService.createRefund("pay_stripe_captured_2500", 250000, "big order", null);
        assertEquals(RefundStatus.PENDING_APPROVAL, refund.getStatus());
        assertTrue(refund.isRequiresApproval());
        // Balance untouched until approval.
        assertEquals(0, paymentRepository.findById("pay_stripe_captured_2500").orElseThrow().getRefundedAmountCents());

        var approved = approvalService.approve(refund.getId());
        assertEquals(RefundStatus.SUCCEEDED, approved.getStatus());
        assertEquals(250000, paymentRepository.findById("pay_stripe_captured_2500").orElseThrow().getRefundedAmountCents());
    }

    @Test
    void largeRefund_canBeRejected() {
        var refund = refundService.createRefund("pay_stripe_captured_2500", 250000, null, null);
        var rejected = approvalService.reject(refund.getId());
        assertEquals(RefundStatus.REJECTED, rejected.getStatus());
        assertEquals(0, paymentRepository.findById("pay_stripe_captured_2500").orElseThrow().getRefundedAmountCents());
    }

    @Test
    void idempotentRetry_returnsSameRefund() {
        var first = refundService.createRefund("pay_stripe_captured_100", 1000, null, "key-123");
        var second = refundService.createRefund("pay_stripe_captured_100", 1000, null, "key-123");
        assertEquals(first.getId(), second.getId());
        // Only charged once.
        assertEquals(1000, paymentRepository.findById("pay_stripe_captured_100").orElseThrow().getRefundedAmountCents());
    }

    @Test
    void sameIdempotencyKey_onDifferentPayments_createsDistinctRefunds() {
        var a = refundService.createRefund("pay_stripe_captured_100", 3000, null, "shared-key");
        var b = refundService.createRefund("pay_adyen_captured_750", 9900, null, "shared-key");
        assertNotEquals(a.getId(), b.getId());
        assertEquals("pay_adyen_captured_750", b.getPaymentId());
        assertEquals(9900, b.getAmountCents());
    }

    @Test
    void sameIdempotencyKey_withDifferentAmount_isConflict() {
        refundService.createRefund("pay_stripe_captured_100", 1000, null, "conflict-key");
        var ex = assertThrows(ApiException.class,
                () -> refundService.createRefund("pay_stripe_captured_100", 2000, null, "conflict-key"));
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", ex.getCode());
    }

    @Test
    void listRefundsForUnknownPayment_isNotFound() {
        var ex = assertThrows(ApiException.class, () -> refundService.listRefundsForPayment("nope"));
        assertEquals("PAYMENT_NOT_FOUND", ex.getCode());
    }

    @Test
    void multiplePartialRefunds_accumulateToFull() {
        // Already €600 of €1000 refunded; refund the remaining €400.
        var refund = refundService.createRefund("pay_adyen_partially_refunded_1000", 40000, null, null);
        assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());
        assertTrue(paymentRepository.findById("pay_adyen_partially_refunded_1000").orElseThrow().isFullyRefunded());
    }

    @Test
    void highRiskRefund_isFlaggedHigh() {
        var refund = refundService.createRefund("pay_stripe_captured_4999", 499900, null, null);
        assertEquals(RiskLevel.HIGH, refund.getRiskLevel());
        assertTrue(refund.isRequiresApproval());
        assertNotEquals(RefundStatus.SUCCEEDED, refund.getStatus());
    }
}
