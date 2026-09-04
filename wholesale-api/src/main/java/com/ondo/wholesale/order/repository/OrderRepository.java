package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
