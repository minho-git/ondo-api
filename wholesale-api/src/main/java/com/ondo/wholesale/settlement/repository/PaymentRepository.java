package com.ondo.wholesale.settlement.repository;

import com.ondo.wholesale.settlement.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
