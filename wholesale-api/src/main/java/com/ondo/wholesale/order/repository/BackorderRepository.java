package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Backorder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackorderRepository extends JpaRepository<Backorder, Long> {
}
