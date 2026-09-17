package com.ondo.wholesale.settlement.repository;

import com.ondo.wholesale.settlement.domain.AllocationIdempotency;
import com.ondo.wholesale.settlement.domain.PaymentIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationIdempotencyRepository
        extends JpaRepository<AllocationIdempotency, PaymentIdempotency.Key> {
}
