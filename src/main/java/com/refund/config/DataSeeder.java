package com.refund.config;

import com.refund.domain.Payment;
import com.refund.domain.PaymentStatus;
import com.refund.domain.Provider;
import com.refund.domain.Refund;
import com.refund.domain.RefundStatus;
import com.refund.domain.RiskLevel;
import com.refund.repository.PaymentRepository;
import com.refund.repository.RefundRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a spread of payments across all providers, statuses, and amounts
 * (€50–€5000), plus the specific edge cases the challenge calls for — each one
 * backed by realistic data (refund balances are produced by actual seeded
 * {@link Refund} records, not faked):
 *
 * <ul>
 *   <li>Fully refunded payment (via two historical refunds)</li>
 *   <li>Partially refunded payment with multiple prior refund attempts</li>
 *   <li>Large refund already PENDING_APPROVAL (ready to approve/reject)</li>
 *   <li>LegacyPay (full-refund-only) and non-CAPTURED payments</li>
 * </ul>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public DataSeeder(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @Override
    public void run(String... args) {
        Instant now = Instant.now();

        // --- Cleanly refundable (no history) ---
        payment("pay_stripe_captured_100", Provider.STRIPE, 10000, PaymentStatus.CAPTURED, now);
        payment("pay_adyen_captured_750", Provider.ADYEN, 75000, PaymentStatus.CAPTURED, now);
        payment("pay_adyen_captured_50", Provider.ADYEN, 5000, PaymentStatus.CAPTURED, now); // min amount

        // --- Large refunds → require approval ---
        payment("pay_stripe_captured_2500", Provider.STRIPE, 250000, PaymentStatus.CAPTURED, now);
        payment("pay_stripe_captured_4999", Provider.STRIPE, 499900, PaymentStatus.CAPTURED, now); // max amount

        // --- LegacyPay: full refunds only ---
        payment("pay_legacypay_captured_500", Provider.LEGACYPAY, 50000, PaymentStatus.CAPTURED, now);
        payment("pay_legacypay_captured_5000", Provider.LEGACYPAY, 500000, PaymentStatus.CAPTURED, now); // full + large

        // --- Not refundable by status ---
        payment("pay_adyen_authorized_300", Provider.ADYEN, 30000, PaymentStatus.AUTHORIZED, now);
        payment("pay_stripe_failed_200", Provider.STRIPE, 20000, PaymentStatus.FAILED, now);
        payment("pay_legacypay_authorized_1200", Provider.LEGACYPAY, 120000, PaymentStatus.AUTHORIZED, now);

        // --- Edge case: PARTIALLY refunded via MULTIPLE prior refund attempts (€300 + €300 of €1000) ---
        Payment partial = payment("pay_adyen_partially_refunded_1000", Provider.ADYEN, 100000,
                PaymentStatus.CAPTURED, now);
        seedSettledRefund(partial, 30000, now);
        seedSettledRefund(partial, 30000, now);

        // --- Edge case: FULLY refunded via two historical refunds (€250 + €150 of €400) ---
        Payment full = payment("pay_stripe_fully_refunded_400", Provider.STRIPE, 40000,
                PaymentStatus.CAPTURED, now);
        seedSettledRefund(full, 25000, now);
        seedSettledRefund(full, 15000, now);

        // --- Edge case: large refund already awaiting approval (approve/reject it out of the box) ---
        Payment pending = payment("pay_stripe_captured_3500", Provider.STRIPE, 350000,
                PaymentStatus.CAPTURED, now);
        seedPendingRefund(pending, 350000, now);

        log.info("Seeded {} payments and {} refunds (providers, statuses, and all edge cases). Try: GET /payments",
                paymentRepository.count(), refundRepository.count());
    }

    private Payment payment(String id, Provider provider, long amountCents, PaymentStatus status, Instant now) {
        return paymentRepository.save(new Payment(id, provider, amountCents, "EUR", status,
                now.minus(1, ChronoUnit.DAYS)));
    }

    /** A historical SUCCEEDED refund — also advances the payment's refunded balance, keeping data consistent. */
    private void seedSettledRefund(Payment payment, long cents, Instant now) {
        Refund refund = new Refund(UUID.randomUUID().toString(), payment.getId(), cents,
                "seeded historical refund", null, now);
        refund.setRiskLevel(RiskLevel.LOW);
        refund.setRiskScore(0);
        refund.setRequiresApproval(false);
        refund.markStatus(RefundStatus.SUCCEEDED, now);
        payment.applyRefund(cents);
        refundRepository.save(refund);
        paymentRepository.save(payment);
    }

    /** A large refund left in PENDING_APPROVAL — funds are NOT reserved until approved. */
    private void seedPendingRefund(Payment payment, long cents, Instant now) {
        Refund refund = new Refund(UUID.randomUUID().toString(), payment.getId(), cents,
                "seeded large refund awaiting approval", null, now);
        refund.setRiskLevel(RiskLevel.HIGH);
        refund.setRiskScore(80);
        refund.setRiskReasons("amount exceeds large-amount threshold (+40); "
                + "amount is >= 50% of the original payment (+20); "
                + "refund clears the entire remaining balance (+20)");
        refund.setRequiresApproval(true);
        refund.markStatus(RefundStatus.PENDING_APPROVAL, now);
        refundRepository.save(refund);
    }
}
