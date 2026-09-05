package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByIdAndWholesalerId(Long id, Long wholesalerId);

    /** 명령(확정·취소·포장)의 주문 내 직렬화 — 항상 이 락을 먼저, variant 락을 그 다음에 잡는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.wholesalerId = :wholesalerId")
    Optional<Order> lockByIdAndWholesalerId(@Param("id") Long id, @Param("wholesalerId") Long wholesalerId);
}
