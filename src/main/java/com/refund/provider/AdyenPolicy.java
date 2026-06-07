package com.refund.provider;

import com.refund.domain.Payment;
import com.refund.domain.Provider;
import org.springframework.stereotype.Component;

/** Adyen supports partial and multiple refunds — no extra constraints. */
@Component
public class AdyenPolicy implements ProviderPolicy {

    @Override
    public Provider provider() {
        return Provider.ADYEN;
    }

    @Override
    public void validateRefund(Payment payment, long amountCents) {
        // No provider-specific restrictions beyond the shared engine rules.
    }
}
