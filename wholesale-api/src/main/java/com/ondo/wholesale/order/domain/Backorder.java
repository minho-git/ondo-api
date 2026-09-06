package com.ondo.wholesale.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 미송. 주문 애그리거트 밖의 독립 엔티티라 order_item 은 Long 으로만 든다 —
 * 미송 티켓(MUL-48)과 공유하는 경계다 (MUL-47).
 *
 * <p>qty 는 <b>원래 미송량</b>으로 불변이다. 남은 미송량은 라인의
 * {@code qty − allocated_qty} 로 파생한다. 라인당 OPEN 미송은 최대 1건.
 */
@Entity
@Table(name = "backorder", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Backorder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private Long orderItemId;

    /** 원래 미송량. 불변. */
    @Column(nullable = false, updatable = false)
    private int qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

    /** FIFO 기준 시각. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 잔량이 0 이 됐을 때만 부른다 — 포장 준비가 라인을 전량 배분한 순간이다. */
    public void resolve() {
        this.status = BackorderStatus.RESOLVED;
    }

    /** 배분취소가 해소를 되돌린다 — "해소했던 미송이 되살아난다"는 계약. */
    public void reopen() {
        this.status = BackorderStatus.OPEN;
    }

    @Builder
    private Backorder(Long orderItemId, int qty) {
        this.orderItemId = orderItemId;
        this.qty = qty;
    }
}
