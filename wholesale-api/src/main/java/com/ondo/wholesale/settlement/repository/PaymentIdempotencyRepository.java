package com.ondo.wholesale.settlement.repository;

import com.ondo.wholesale.settlement.domain.PaymentIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotency, PaymentIdempotency.Key> {
}
