package com.refund.repository;

import com.refund.domain.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /**
     * Loads a payment with a row-level write lock so the read-modify-write of the
     * refunded balance is serialized across concurrent refund requests on the same
     * payment. Without this, parallel refunds can each pass the over-refund check
     * before any commits, refunding more than was captured.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") String id);
}
