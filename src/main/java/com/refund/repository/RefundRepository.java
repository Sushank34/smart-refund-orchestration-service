package com.refund.repository;

import com.refund.domain.Refund;
import com.refund.domain.RiskLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, String> {

    Optional<Refund> findByIdempotencyKeyAndPaymentId(String idempotencyKey, String paymentId);

    List<Refund> findByPaymentId(String paymentId);

    long countByPaymentId(String paymentId);

    List<Refund> findByRiskLevel(RiskLevel riskLevel);
}
