package com.ondo.wholesale.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 포장 항목. 배분 결과 한 줄이다 (MUL-47).
 *
 * <p>deleted_at 이 값이면 배분취소 — allocated 가 줄고 미송이 부활한 기록이다.
 * backorder_id 가 값이면 그 미송을 해소한 배분분이라는 뜻이다.
 */
@Entity
@Table(name = "packing_item", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PackingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packing_id", nullable = false, updatable = false)
    private Packing packing;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private Long orderItemId;

    /** 값 = 미송 배분분(해소 기록). */
    @Column(name = "backorder_id", updatable = false)
    private Long backorderId;

    @Column(name = "allocation_batch_id", nullable = false, updatable = false)
    private Long allocationBatchId;

    /** 배분 수량. 출고 수량이 아니다. */
    @Column(nullable = false, updatable = false)
    private int qty;

    /** 삭제 = 배분취소 = 미송부활. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 배분취소 — 행은 남기고 deleted_at 만 찍는다. */
    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    PackingItem(Packing packing, Long orderItemId, Long backorderId, Long allocationBatchId, int qty) {
        this.packing = packing;
        this.orderItemId = orderItemId;
        this.backorderId = backorderId;
        this.allocationBatchId = allocationBatchId;
        this.qty = qty;
    }
}
