package com.refund.provider;

import com.refund.domain.Payment;
import com.refund.domain.Provider;

/**
 * Provider-specific refund rules. Keeping these polymorphic (rather than an
 * if/else chain in the service) means adding a provider is a new class, not an
 * edit to the engine.
 */
public interface ProviderPolicy {

    Provider provider();

    /**
     * Validate a refund of {@code amountCents} against the payment for this
     * provider. Throw {@link com.refund.exception.ApiException} on violation.
     */
    void validateRefund(Payment payment, long amountCents);
}
