package com.refund.web.dto;

import com.refund.domain.Payment;
import com.refund.domain.PaymentStatus;
import com.refund.domain.Provider;
import com.refund.service.Money;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        Provider provider,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        BigDecimal refundedAmount,
        BigDecimal remainingRefundable,
        String refundState,
        Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getProvider(),
                Money.toMajorUnits(p.getAmountCents()),
                p.getCurrency(),
                p.getStatus(),
                Money.toMajorUnits(p.getRefundedAmountCents()),
                Money.toMajorUnits(p.remainingRefundableCents()),
                refundState(p),
                p.getCreatedAt());
    }

    private static String refundState(Payment p) {
        if (p.getRefundedAmountCents() == 0) {
            return "NOT_REFUNDED";
        }
        return p.isFullyRefunded() ? "FULLY_REFUNDED" : "PARTIALLY_REFUNDED";
    }
}
