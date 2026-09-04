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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * 주문 라인. 수량 사다리(shipped ≤ allocated ≤ qty)는 DB CHECK 가 마지막 그물이고,
 * 서비스가 선검증해 에러 코드로 변환한다 (MUL-47).
 */
@Entity
@Table(name = "order_item", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    /** 주문수량. */
    @Column(nullable = false, updatable = false)
    private int qty;

    /** 주문 시점 판매가 스냅샷. */
    @Column(name = "unit_price", nullable = false, updatable = false)
    private int unitPrice;

    /** 할당 카운터. 갱신 트랜잭션은 행을 FOR UPDATE 로 잡는다. */
    @Column(name = "allocated_qty", nullable = false)
    private int allocatedQty;

    /** 출고 카운터. 출고 티켓이 올린다. */
    @Column(name = "shipped_qty", nullable = false)
    private int shippedQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    OrderItem(Order order, Long variantId, int qty, int unitPrice) {
        this.order = order;
        this.variantId = variantId;
        this.qty = qty;
        this.unitPrice = unitPrice;
    }
}
