package com.ondo.wholesale.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 거래처. 소매상은 다른 DB 라 retailerId 는 논리 참조고, 상호·전화번호는 스냅샷이다 (MUL-47).
 *
 * <p>retailer_phone 은 소매 주문 접수 API 가 채운다 — 값이 들어오기 전에는 NULL.
 * trade_type·credit_days·receivable_balance 는 정산 쪽 컬럼이라 매핑하지 않는다
 * (insert 는 DB DEFAULT 로 통과한다). 그 작업이 쓰기 시작할 때 추가한다.
 */
@Entity
@Table(name = "partner", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wholesaler_id", nullable = false, updatable = false)
    private Long wholesalerId;

    /** 논리 참조(경계). FK 없음. */
    @Column(name = "retailer_id", nullable = false, updatable = false)
    private Long retailerId;

    /** 소매 상호 스냅샷 (U-15). */
    @Column(name = "retailer_name", nullable = false, length = 50)
    private String retailerName;

    /** 소매 전화번호 스냅샷 (V6). */
    @Column(name = "retailer_phone", length = 20)
    private String retailerPhone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Partner(Long wholesalerId, Long retailerId, String retailerName, String retailerPhone) {
        this.wholesalerId = wholesalerId;
        this.retailerId = retailerId;
        this.retailerName = retailerName;
        this.retailerPhone = retailerPhone;
    }
}
