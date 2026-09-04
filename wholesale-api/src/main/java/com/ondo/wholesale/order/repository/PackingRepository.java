package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Packing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingRepository extends JpaRepository<Packing, Long> {
}
