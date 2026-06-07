package com.refund.web.dto;

import com.refund.domain.Refund;
import com.refund.domain.RefundStatus;
import com.refund.domain.RiskLevel;
import com.refund.service.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RefundResponse(
        String id,
        String paymentId,
        BigDecimal amount,
        RefundStatus status,
        String reason,
        RiskLevel riskLevel,
        int riskScore,
        List<String> riskReasons,
        boolean requiresApproval,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public static RefundResponse from(Refund r) {
        return new RefundResponse(
                r.getId(),
                r.getPaymentId(),
                Money.toMajorUnits(r.getAmountCents()),
                r.getStatus(),
                r.getReason(),
                r.getRiskLevel(),
                r.getRiskScore(),
                splitReasons(r.getRiskReasons()),
                r.isRequiresApproval(),
                r.getIdempotencyKey(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private static List<String> splitReasons(String reasons) {
        if (reasons == null || reasons.isBlank()) {
            return List.of();
        }
        return List.of(reasons.split("; "));
    }
}
