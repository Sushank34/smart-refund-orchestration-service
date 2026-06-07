# API Reference

Base URL: `http://localhost:8085`
All amounts are in **major units** (e.g. `100.00` = €100). All responses are JSON.

---

## Payments

### List payments
```bash
curl http://localhost:8085/payments
```

### Get a payment
```bash
curl http://localhost:8085/payments/pay_stripe_captured_100
```
```json
{
  "id": "pay_stripe_captured_100",
  "provider": "STRIPE",
  "amount": 100.00,
  "currency": "EUR",
  "status": "CAPTURED",
  "refundedAmount": 0.00,
  "remainingRefundable": 100.00,
  "refundState": "NOT_REFUNDED",
  "createdAt": "2026-06-06T08:00:00Z"
}
```

---

## Refunds

### Create a refund
`POST /payments/{paymentId}/refunds`

| Field | In | Required | Notes |
|-------|----|----------|-------|
| `amount` | body | yes | major units, must be positive |
| `reason` | body | no | free text |
| `Idempotency-Key` | header | no | retries with the same key return the original refund |

**Success (small refund, settles immediately) → `201`:**
```bash
curl -X POST http://localhost:8085/payments/pay_stripe_captured_100/refunds \
  -H "Content-Type: application/json" \
  -d '{"amount": 30.00, "reason": "item returned"}'
```
```json
{
  "id": "…",
  "paymentId": "pay_stripe_captured_100",
  "amount": 30.00,
  "status": "SUCCEEDED",
  "riskLevel": "LOW",
  "riskScore": 0,
  "riskReasons": [],
  "requiresApproval": false
}
```

> `riskReasons` lists the exact signals that contributed to the score, e.g.
> `["amount exceeds large-amount threshold (+40)", "refund clears the entire remaining balance (+20)"]`.

### Get a refund
```bash
curl http://localhost:8085/refunds/{refundId}
```

### List a payment's refund history
```bash
curl http://localhost:8085/payments/pay_adyen_partially_refunded_1000/refunds
```

### List refunds / filter by risk
```bash
curl http://localhost:8085/refunds
curl "http://localhost:8085/refunds?riskLevel=HIGH"
```

### Approve / reject a pending refund
```bash
curl -X POST http://localhost:8085/refunds/{refundId}/approve
curl -X POST http://localhost:8085/refunds/{refundId}/reject
```

---

## Worked examples by edge case

**Large refund → requires approval, then approve:**
```bash
# 1. Create — comes back PENDING_APPROVAL, funds not yet reserved
curl -X POST http://localhost:8085/payments/pay_stripe_captured_2500/refunds \
  -H "Content-Type: application/json" -d '{"amount": 2500.00}'

# 2. Approve using the returned id → SUCCEEDED, balance now updated
curl -X POST http://localhost:8085/refunds/<id-from-step-1>/approve
```

**LegacyPay rejects partial, accepts full:**
```bash
# Partial → 422 PARTIAL_REFUND_NOT_SUPPORTED
curl -X POST http://localhost:8085/payments/pay_legacypay_captured_500/refunds \
  -H "Content-Type: application/json" -d '{"amount": 100.00}'

# Full → 201 SUCCEEDED
curl -X POST http://localhost:8085/payments/pay_legacypay_captured_500/refunds \
  -H "Content-Type: application/json" -d '{"amount": 500.00}'
```

**Over-refund → 422:**
```bash
curl -X POST http://localhost:8085/payments/pay_adyen_captured_50/refunds \
  -H "Content-Type: application/json" -d '{"amount": 60.00}'
```

**Refund a non-captured payment → 422:**
```bash
curl -X POST http://localhost:8085/payments/pay_adyen_authorized_300/refunds \
  -H "Content-Type: application/json" -d '{"amount": 50.00}'
```

**Idempotent retry → same refund, charged once:**
```bash
curl -X POST http://localhost:8085/payments/pay_stripe_captured_100/refunds \
  -H "Content-Type: application/json" -H "Idempotency-Key: abc-123" \
  -d '{"amount": 10.00}'
# Repeat the exact request → identical refund id, balance unchanged
```

**Multiple partials accumulate to full:**
```bash
# pay_adyen_partially_refunded_1000 already has €600 of €1000 refunded
curl -X POST http://localhost:8085/payments/pay_adyen_partially_refunded_1000/refunds \
  -H "Content-Type: application/json" -d '{"amount": 400.00}'
# → SUCCEEDED, payment.refundState becomes FULLY_REFUNDED
```

---

## Error envelope

Every failure returns:
```json
{ "status": 422, "code": "MACHINE_CODE", "error": "Human-readable message" }
```

| HTTP | Code | When |
|------|------|------|
| 400 | `VALIDATION_ERROR` | malformed/missing body field |
| 400 | `INVALID_PARAMETER` | bad query-param value (e.g. unknown `riskLevel`) |
| 404 | `PAYMENT_NOT_FOUND` / `REFUND_NOT_FOUND` | unknown id |
| 405 | `METHOD_NOT_ALLOWED` | unsupported HTTP method for the route |
| 422 | `PAYMENT_NOT_REFUNDABLE` | payment not `CAPTURED` |
| 422 | `INVALID_AMOUNT` | amount ≤ 0 |
| 422 | `ALREADY_FULLY_REFUNDED` | nothing left to refund |
| 422 | `REFUND_EXCEEDS_REMAINING` | over-refund |
| 422 | `PARTIAL_REFUND_NOT_SUPPORTED` | LegacyPay partial refund |
| 409 | `REFUND_NOT_PENDING_APPROVAL` | approve/reject on a non-pending refund |
