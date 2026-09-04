package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndWholesalerId(Long id, Long wholesalerId);
}
