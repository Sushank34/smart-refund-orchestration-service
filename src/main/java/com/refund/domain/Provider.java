package com.refund.domain;

/** Payment providers the orchestrator supports. */
public enum Provider {
    STRIPE,
    ADYEN,
    LEGACYPAY
}
