package com.refund.domain;

/**
 * Refund state machine:
 *
 *   PENDING_APPROVAL ──approve──► SUCCEEDED
 *                    ──reject───► REJECTED
 *   (no approval needed) ───────► SUCCEEDED
 *   (validation/provider error) ► FAILED
 */
public enum RefundStatus {
    PENDING_APPROVAL,
    SUCCEEDED,
    REJECTED,
    FAILED
}
