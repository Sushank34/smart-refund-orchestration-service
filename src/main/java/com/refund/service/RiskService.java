package com.refund.service;

import com.refund.domain.Payment;
import com.refund.domain.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Deterministic, rule-based risk scoring. Cheap, explainable, and good enough
 * to drive the approval gate — far better ROI than an ML model for this domain.
 *
 * <pre>
 *   +40  refund amount exceeds the large-amount threshold (default €1000)
 *   +20  refund is &ge; 50% of the original payment
 *   +20  this is the 3rd or later refund attempt on the payment (velocity)
 *   +20  refund clears the entire remaining balance (full refund)
 * </pre>
 *
 * Level: {@code >= highThreshold} HIGH, {@code >= mediumThreshold} MEDIUM, else LOW.
 */
@Service
public class RiskService {

    private final long largeAmountThresholdCents;
    private final int highThreshold;
    private final int mediumThreshold;

    public RiskService(
            @Value("${refund.approval-threshold-cents}") long largeAmountThresholdCents,
            @Value("${refund.risk.high-threshold}") int highThreshold,
            @Value("${refund.risk.medium-threshold}") int mediumThreshold) {
        this.largeAmountThresholdCents = largeAmountThresholdCents;
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    public RiskAssessment assess(Payment payment, long amountCents, long priorRefundCount) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (amountCents > largeAmountThresholdCents) {
            score += 40;
            reasons.add("amount exceeds large-amount threshold (+40)");
        }
        if (amountCents * 2 >= payment.getAmountCents()) {
            score += 20;
            reasons.add("amount is >= 50% of the original payment (+20)");
        }
        if (priorRefundCount >= 2) {
            score += 20;
            reasons.add("3rd or later refund attempt on this payment (+20)");
        }
        if (amountCents == payment.remainingRefundableCents()) {
            score += 20;
            reasons.add("refund clears the entire remaining balance (+20)");
        }

        RiskLevel level;
        if (score >= highThreshold) {
            level = RiskLevel.HIGH;
        } else if (score >= mediumThreshold) {
            level = RiskLevel.MEDIUM;
        } else {
            level = RiskLevel.LOW;
        }
        return new RiskAssessment(score, level, reasons);
    }
}
