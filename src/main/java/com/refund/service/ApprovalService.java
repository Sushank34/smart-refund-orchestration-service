package com.refund.service;

import com.refund.domain.Payment;
import com.refund.domain.Refund;
import com.refund.domain.RefundStatus;
import com.refund.exception.ApiException;
import com.refund.repository.PaymentRepository;
import com.refund.repository.RefundRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles the manual approve/reject decision for refunds held for review. */
@Service
public class ApprovalService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;

    public ApprovalService(RefundRepository refundRepository,
                           PaymentRepository paymentRepository,
                           RefundService refundService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;
    }

    @Transactional
    public Refund approve(String refundId) {
        Refund refund = loadPending(refundId);
        // Locked read: serializes settlement against concurrent refunds on the same payment.
        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND",
                        "No payment with id " + refund.getPaymentId()));

        Instant now = Instant.now();
        // Re-validate: other refunds may have settled while this one waited.
        if (refund.getAmountCents() > payment.remainingRefundableCents()) {
            refund.markStatus(RefundStatus.FAILED, now);
            refundRepository.save(refund);
            throw ApiException.unprocessable("REFUND_EXCEEDS_REMAINING",
                    "Refund can no longer be settled — remaining balance is now "
                            + payment.remainingRefundableCents() + " cents.");
        }

        refundService.settle(payment, refund, now);
        return refundRepository.save(refund);
    }

    @Transactional
    public Refund reject(String refundId) {
        Refund refund = loadPending(refundId);
        refund.markStatus(RefundStatus.REJECTED, Instant.now());
        return refundRepository.save(refund);
    }

    private Refund loadPending(String refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> ApiException.notFound("REFUND_NOT_FOUND", "No refund with id " + refundId));
        if (refund.getStatus() != RefundStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("REFUND_NOT_PENDING_APPROVAL",
                    "Refund is " + refund.getStatus() + " and cannot be approved or rejected.");
        }
        return refund;
    }
}
