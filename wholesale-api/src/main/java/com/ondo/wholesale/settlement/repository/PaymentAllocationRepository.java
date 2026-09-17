package com.ondo.wholesale.settlement.repository;

import com.ondo.wholesale.settlement.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
}
