package com.refund.domain;

/** Lifecycle of the original payment. Only CAPTURED funds can be refunded. */
public enum PaymentStatus {
    AUTHORIZED, // funds reserved but not captured — not refundable yet
    CAPTURED,   // funds settled — refundable
    FAILED      // never succeeded — not refundable
}
