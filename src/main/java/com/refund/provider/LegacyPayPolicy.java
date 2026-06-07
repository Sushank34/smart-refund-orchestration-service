package com.refund.provider;

import com.refund.domain.Payment;
import com.refund.domain.Provider;
import com.refund.exception.ApiException;
import org.springframework.stereotype.Component;

/**
 * LegacyPay only supports full refunds. Any amount that is not the entire
 * remaining balance is rejected.
 */
@Component
public class LegacyPayPolicy implements ProviderPolicy {

    @Override
    public Provider provider() {
        return Provider.LEGACYPAY;
    }

    @Override
    public void validateRefund(Payment payment, long amountCents) {
        if (amountCents != payment.remainingRefundableCents()) {
            throw ApiException.unprocessable(
                    "PARTIAL_REFUND_NOT_SUPPORTED",
                    "LegacyPay only supports full refunds. Refund the full remaining balance of "
                            + payment.remainingRefundableCents() + " cents.");
        }
    }
}
