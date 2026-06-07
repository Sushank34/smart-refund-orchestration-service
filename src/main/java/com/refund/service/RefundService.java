package com.refund.service;

import com.refund.domain.Payment;
import com.refund.domain.PaymentStatus;
import com.refund.domain.Refund;
import com.refund.domain.RefundStatus;
import com.refund.exception.ApiException;
import com.refund.provider.ProviderPolicyFactory;
import com.refund.repository.PaymentRepository;
import com.refund.repository.RefundRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The refund engine. Enforces every business rule, scores risk, and decides
 * whether a refund settles immediately or needs manual approval.
 */
@Service
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ProviderPolicyFactory policyFactory;
    private final RiskService riskService;
    private final long approvalThresholdCents;

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         ProviderPolicyFactory policyFactory,
                         RiskService riskService,
                         @Value("${refund.approval-threshold-cents}") long approvalThresholdCents) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.policyFactory = policyFactory;
        this.riskService = riskService;
        this.approvalThresholdCents = approvalThresholdCents;
    }

    @Transactional
    public Refund createRefund(String paymentId, long amountCents, String reason, String idempotencyKey) {
        // Idempotency: a retried request with the same key on the SAME payment returns the
        // original refund. Scoping to the payment prevents a reused key from returning an
        // unrelated refund. A reuse with a DIFFERENT amount is a client error, not a replay.
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = refundRepository.findByIdempotencyKeyAndPaymentId(idempotencyKey, paymentId);
            if (existing.isPresent()) {
                Refund prior = existing.get();
                if (prior.getAmountCents() != amountCents) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_CONFLICT",
                            "Idempotency-Key was already used for this payment with a different amount.");
                }
                return prior;
            }
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND",
                        "No payment with id " + paymentId));

        validateRefundable(payment, amountCents);

        // Provider-specific rules (e.g. LegacyPay rejects partial refunds).
        policyFactory.forProvider(payment.getProvider()).validateRefund(payment, amountCents);

        long priorAttempts = refundRepository.countByPaymentId(paymentId);
        RiskAssessment risk = riskService.assess(payment, amountCents, priorAttempts);

        Instant now = Instant.now();
        Refund refund = new Refund(UUID.randomUUID().toString(), paymentId, amountCents,
                reason, StringUtils.hasText(idempotencyKey) ? idempotencyKey : null, now);
        refund.setRiskScore(risk.score());
        refund.setRiskLevel(risk.level());
        refund.setRiskReasons(String.join("; ", risk.reasons()));

        boolean needsApproval = amountCents > approvalThresholdCents || risk.level() == com.refund.domain.RiskLevel.HIGH;
        refund.setRequiresApproval(needsApproval);

        if (needsApproval) {
            // Hold for review — funds are NOT reserved until approval.
            refund.markStatus(RefundStatus.PENDING_APPROVAL, now);
        } else {
            settle(payment, refund, now);
        }

        return refundRepository.save(refund);
    }

    /** Apply a refund to the payment balance and mark it succeeded. */
    void settle(Payment payment, Refund refund, Instant now) {
        payment.applyRefund(refund.getAmountCents());
        paymentRepository.save(payment);
        refund.markStatus(RefundStatus.SUCCEEDED, now);
    }

    /** Shared validation, also re-run at approval time. */
    void validateRefundable(Payment payment, long amountCents) {
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw ApiException.unprocessable("PAYMENT_NOT_REFUNDABLE",
                    "Only CAPTURED payments can be refunded. Payment is " + payment.getStatus() + ".");
        }
        if (amountCents <= 0) {
            throw ApiException.unprocessable("INVALID_AMOUNT", "Refund amount must be positive.");
        }
        if (payment.isFullyRefunded()) {
            throw ApiException.unprocessable("ALREADY_FULLY_REFUNDED",
                    "Payment has already been fully refunded.");
        }
        if (amountCents > payment.remainingRefundableCents()) {
            throw ApiException.unprocessable("REFUND_EXCEEDS_REMAINING",
                    "Refund of " + amountCents + " cents exceeds the remaining refundable balance of "
                            + payment.remainingRefundableCents() + " cents.");
        }
    }

    @Transactional(readOnly = true)
    public Refund getRefund(String id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("REFUND_NOT_FOUND", "No refund with id " + id));
    }

    @Transactional(readOnly = true)
    public List<Refund> listRefunds() {
        return refundRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Refund> listRefundsByRisk(com.refund.domain.RiskLevel level) {
        return refundRepository.findByRiskLevel(level);
    }

    @Transactional(readOnly = true)
    public List<Refund> listRefundsForPayment(String paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw ApiException.notFound("PAYMENT_NOT_FOUND", "No payment with id " + paymentId);
        }
        return refundRepository.findByPaymentId(paymentId);
    }
}
