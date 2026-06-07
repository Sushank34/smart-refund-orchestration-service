package com.refund.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A refund attempt against a {@link Payment}. {@code idempotencyKey} is unique
 * <em>per payment</em> so a retried request on the same payment returns the
 * original refund, while the same key on a different payment is independent.
 */
@Entity
@Table(name = "refunds", uniqueConstraints =
        @UniqueConstraint(name = "uk_refund_idem_payment", columnNames = {"idempotency_key", "payment_id"}))
public class Refund {

    @Id
    private String id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    private int riskScore;

    /** Human-readable signals that produced the score, joined by "; ". */
    @Column(length = 1000)
    private String riskReasons;

    @Column(nullable = false)
    private boolean requiresApproval;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Refund() {
        // for JPA
    }

    public Refund(String id, String paymentId, long amountCents, String reason,
                  String idempotencyKey, Instant now) {
        this.id = id;
        this.paymentId = paymentId;
        this.amountCents = amountCents;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markStatus(RefundStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskReasons() {
        return riskReasons;
    }

    public void setRiskReasons(String riskReasons) {
        this.riskReasons = riskReasons;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
