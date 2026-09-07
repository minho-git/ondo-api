package com.ondo.wholesale.outbound.repository;

import com.ondo.wholesale.outbound.domain.Outbound;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OutboundRepository extends JpaRepository<Outbound, Long>, JpaSpecificationExecutor<Outbound> {

    Optional<Outbound> findByIdAndWholesalerId(Long id, Long wholesalerId);

    /** 출고 확정의 직렬화 지점 — 이 락이 맨 앞, 그 다음이 주문 행(id asc)·variant(id asc)·채번이다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Outbound o where o.id = :id and o.wholesalerId = :wholesalerId")
    Optional<Outbound> lockByIdAndWholesalerId(@Param("id") Long id, @Param("wholesalerId") Long wholesalerId);
}
