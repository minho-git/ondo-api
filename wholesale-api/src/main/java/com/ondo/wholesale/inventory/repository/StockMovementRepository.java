package com.ondo.wholesale.inventory.repository;

import com.ondo.wholesale.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
