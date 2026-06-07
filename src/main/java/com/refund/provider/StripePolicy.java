package com.refund.provider;

import com.refund.domain.Payment;
import com.refund.domain.Provider;
import org.springframework.stereotype.Component;

/** Stripe supports partial and multiple refunds — no extra constraints. */
@Component
public class StripePolicy implements ProviderPolicy {

    @Override
    public Provider provider() {
        return Provider.STRIPE;
    }

    @Override
    public void validateRefund(Payment payment, long amountCents) {
        // No provider-specific restrictions beyond the shared engine rules.
    }
}
