package com.refund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Original payment. Money is held as integer cents to avoid floating-point
 * rounding errors. {@code refundedAmountCents} accumulates across all
 * successful refunds and is the source of truth for over-refund checks.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private long refundedAmountCents;

    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() {
        // for JPA
    }

    public Payment(String id, Provider provider, long amountCents, String currency,
                   PaymentStatus status, Instant createdAt) {
        this.id = id;
        this.provider = provider;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status;
        this.refundedAmountCents = 0L;
        this.createdAt = createdAt;
    }

    /** Cents still available to refund. */
    public long remainingRefundableCents() {
        return amountCents - refundedAmountCents;
    }

    public boolean isFullyRefunded() {
        return refundedAmountCents >= amountCents;
    }

    /** Apply a settled refund to the running total. */
    public void applyRefund(long cents) {
        this.refundedAmountCents += cents;
    }

    public String getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getRefundedAmountCents() {
        return refundedAmountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
