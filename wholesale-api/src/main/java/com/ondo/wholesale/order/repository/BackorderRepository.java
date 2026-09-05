package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Backorder;
import com.ondo.wholesale.order.domain.BackorderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BackorderRepository extends JpaRepository<Backorder, Long> {

    /** 라인당 OPEN 미송은 최대 1건이 불변식 — 위반 데이터가 있어도 FIFO 첫 건만 잡는 방어. */
    Optional<Backorder> findFirstByOrderItemIdAndStatusOrderByCreatedAtAsc(
            Long orderItemId, BackorderStatus status);
}
