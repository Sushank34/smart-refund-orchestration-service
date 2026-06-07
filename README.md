# Smart Refund Orchestration Service

A backend service that orchestrates refunds across multiple payment providers
(**Stripe**, **Adyen**, **LegacyPay**), enforcing per-provider rules, accumulating
partial refunds, scoring fraud/abuse risk, and gating large or high-risk refunds
behind manual approval.

Built with **Spring Boot 3 + JPA + H2 (in-memory)** — no external infrastructure
to run.

## Live demo

| | |
|---|---|
| **Interactive API (Swagger UI)** | https://smart-refund-orchestration-service.onrender.com |
| **Health** | https://smart-refund-orchestration-service.onrender.com/health |

> Hosted on Render's free tier — it sleeps after ~15 min idle, so the **first
> request may take ~30–60s** to cold-start, then responds normally.

---

## Quick start

```bash
mvn spring-boot:run          # starts on http://localhost:8085
mvn test                     # runs the full test suite (21 tests)
```

Once running locally, the interactive API docs are at
`http://localhost:8085/swagger-ui/index.html`.

On startup the service seeds payments (all providers × all statuses × amounts
€50–€5000) **and refund history** covering every edge case — pre-refunded
balances are produced by real seeded `Refund` records, not faked. The startup
log reports the counts (`Seeded N payments and M refunds ...`). List them:

```bash
curl http://localhost:8085/payments
curl http://localhost:8085/refunds
```

H2 console (for inspecting data): `http://localhost:8085/h2-console`
(JDBC URL `jdbc:h2:mem:refunds`, user `sa`, no password).

---

## Domain model

| Entity | Purpose |
|--------|---------|
| **Payment** | The original charge. Holds `amountCents`, `status`, and `refundedAmountCents` (the running total used for over-refund checks). |
| **Refund** | A refund attempt against a payment. Carries `status`, `riskScore`/`riskLevel`, `requiresApproval`, and a unique `idempotencyKey`. |

Money is stored as **integer cents** end-to-end to avoid floating-point rounding.
The API accepts and returns amounts in major units (e.g. `100.00` = €100).

### State machines

**Payment status** (seeded, read-only — only `CAPTURED` is refundable):
```
AUTHORIZED   — funds reserved, not captured → NOT refundable
CAPTURED     — settled                      → refundable
FAILED       — never succeeded              → NOT refundable
```

**Refund status:**
```
                  ┌── approve ──► SUCCEEDED
PENDING_APPROVAL ─┤
                  └── reject  ──► REJECTED

(no approval needed) ───────────► SUCCEEDED
(validation/provider failure) ──► FAILED
```

**Payment refund state** (derived from amounts, exposed on the payment):
```
NOT_REFUNDED → PARTIALLY_REFUNDED → FULLY_REFUNDED
```

---

## Business rules (the refund engine)

1. **Only `CAPTURED` payments are refundable.** `AUTHORIZED`/`FAILED` → `422 PAYMENT_NOT_REFUNDABLE`.
2. **No over-refund.** `requested + alreadyRefunded ≤ originalAmount`, else `422 REFUND_EXCEEDS_REMAINING`.
3. **Already fully refunded** → `422 ALREADY_FULLY_REFUNDED`.
4. **Amount must be positive** → `422 INVALID_AMOUNT`.
5. **LegacyPay supports full refunds only** — any partial amount → `422 PARTIAL_REFUND_NOT_SUPPORTED`.
   Stripe and Adyen allow partial and multiple refunds.
6. **Large or high-risk refunds require approval.** Amount > €1000 **or** risk level `HIGH`
   → refund is created `PENDING_APPROVAL` and **funds are not reserved** until approved.
7. **Idempotency.** A request carrying an `Idempotency-Key` header that was already used
   **on the same payment** returns the original refund — never double-refunds. Reusing the
   key with a different amount is a client error → `409 IDEMPOTENCY_KEY_CONFLICT`.
8. **Partial refunds accumulate.** Multiple refunds sum toward the full amount; the last one
   flips the payment to `FULLY_REFUNDED`.

Provider-specific behaviour lives behind a `ProviderPolicy` interface
(`StripePolicy`, `AdyenPolicy`, `LegacyPayPolicy`) so adding a provider is a new class,
not an edit to the engine.

### Concurrency safety

The over-refund guard is a read-modify-write on the payment's refunded balance, so it must
be safe under concurrent refunds. The engine loads the payment with a **pessimistic row lock**
(`SELECT … FOR UPDATE`) in both `createRefund` and approval-time settlement, serializing
refunds **per payment**. Refunds on *different* payments still run fully in parallel; only
contention on the *same* payment blocks. This is verified by firing 10 parallel refunds at a
single payment — exactly the count that fits the balance settles, and the balance reconciles
with the settled refunds. (Portable to Postgres/MySQL; configure a lock timeout in production.)

---

## Risk monitoring

Deterministic, explainable, rule-based scoring (`RiskService`):

| Signal | Points |
|--------|-------:|
| Amount over the large-amount threshold (default €1000) | +40 |
| Amount ≥ 50% of the original payment | +20 |
| 3rd or later refund attempt on the payment (velocity) | +20 |
| Refund clears the entire remaining balance (full refund) | +20 |

**Level:** `≥70` → `HIGH`, `≥30` → `MEDIUM`, else `LOW`. A `HIGH` level forces manual
approval regardless of amount. Each refund also carries a `riskReasons` array
explaining exactly which signals fired. Inspect flagged refunds:

```bash
curl "http://localhost:8085/refunds?riskLevel=HIGH"
```

---

## Edge cases covered (and seeded)

| Edge case | Seeded payment | Result |
|-----------|----------------|--------|
| Partial refund on captured payment | `pay_stripe_captured_100` | succeeds, balance updated |
| Refund an `AUTHORIZED` payment | `pay_adyen_authorized_300` | `422 PAYMENT_NOT_REFUNDABLE` |
| Refund a `FAILED` payment | `pay_stripe_failed_200` | `422 PAYMENT_NOT_REFUNDABLE` |
| Over-refund | `pay_adyen_captured_50` | `422 REFUND_EXCEEDS_REMAINING` |
| Already fully refunded | `pay_stripe_fully_refunded_400` | `422 ALREADY_FULLY_REFUNDED` |
| LegacyPay partial refund | `pay_legacypay_captured_500` | `422 PARTIAL_REFUND_NOT_SUPPORTED` |
| LegacyPay full refund | `pay_legacypay_captured_500` | succeeds |
| Large refund needs approval (runtime) | `pay_stripe_captured_2500` | `PENDING_APPROVAL` → approve → `SUCCEEDED` |
| Large refund pre-seeded as pending | `pay_stripe_captured_3500` | seeded `PENDING_APPROVAL` refund — approve/reject it directly |
| High-risk refund flagged | `pay_stripe_captured_4999` | `HIGH`, requires approval |
| Duplicate request (idempotency) | any | returns original refund |
| Multiple refund attempts (history) | `pay_adyen_partially_refunded_1000` | seeded with 2 prior €300 refunds (€600 of €1000) |
| Multiple partials accumulate to full | `pay_adyen_partially_refunded_1000` | refund the remaining €400 → flips to `FULLY_REFUNDED` |

---

## API

See [API.md](API.md) for the full endpoint reference and copy-paste curl commands.

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/payments` | list payments |
| `GET`  | `/payments/{id}` | get a payment + refund summary |
| `POST` | `/payments/{id}/refunds` | create a refund (`Idempotency-Key` header optional, scoped per payment) |
| `GET`  | `/payments/{id}/refunds` | full refund history for a payment |
| `GET`  | `/refunds` | list refunds (`?riskLevel=HIGH` filter) |
| `GET`  | `/refunds/{id}` | get a refund |
| `POST` | `/refunds/{id}/approve` | approve a pending refund |
| `POST` | `/refunds/{id}/reject` | reject a pending refund |
| `GET`  | `/health` | liveness check → `{"status":"UP"}` |
| `GET`  | `/` | redirects to the Swagger UI |

Errors use a consistent envelope:
```json
{ "status": 422, "code": "REFUND_EXCEEDS_REMAINING", "error": "Refund of ... exceeds ..." }
```

---

## Project structure

```
src/main/java/com/refund/
├── RefundApplication.java
├── domain/        Payment, Refund + enums (Provider, PaymentStatus, RefundStatus, RiskLevel)
├── repository/    PaymentRepository, RefundRepository (Spring Data JPA)
├── provider/      ProviderPolicy + Stripe/Adyen/LegacyPay impls + factory
├── service/       RefundService (engine), RiskService, ApprovalService, Money
├── web/           Payment/Refund/Health/Home controllers, GlobalExceptionHandler, dto/
├── exception/     ApiException
└── config/        DataSeeder, OpenApiConfig
src/test/java/com/refund/
├── RefundServiceTest.java   one test per business rule / edge case
└── RefundApiTest.java       HTTP status codes + error envelope
```

---

## Scope — deliberately NOT implemented

To stay focused on the scored requirements, the following are intentionally out of scope:
real provider HTTP/SDK calls (providers are modelled as policy classes), authentication,
async/webhooks/retries, multi-currency (all EUR), pagination, and a persistent database
(H2 in-memory is used for zero-setup runs). Each is a known extension point, not an oversight.
