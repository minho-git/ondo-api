package com.ondo.wholesale.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 입금을 주문에 붙인 기록 한 줄 (MUL-124). 돈이 오간 게 아니라 이름표라 원장에는 안 적힌다.
 * 출고된 금액에만 붙인다 — 먼저 받은 돈은 선수금으로 남는다. 취소는 지우지 않고 시각만 찍는다(MUL-127).
 */
@Entity
@Table(name = "payment_allocation", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PaymentAllocation(Long paymentId, Long orderId, long amount) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
    }
}
