package com.ondo.wholesale.outbound.domain;

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
 * 출고(봉투 1개 = 1행) (MUL-49). 포장({@code Packing})·주문은 이 애그리거트 밖이라
 * Long 으로만 든다 — 봉투에 담긴 포장은 {@code packing.outbound_id}가 가리킨다.
 *
 * <p>상태 컬럼이 없다 (D-074) — {@code shippedAt}이 NULL 이면 포장완료, 값이 있으면
 * 출고완료다. {@code statementNumber}도 NULL = 미확정. 확정 후에는 영구 동결이며
 * 동결은 플래그가 아니라 서비스 가드가 보장한다.
 */
@Entity
@Table(name = "outbound", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 비정규화 (D-071) — 스코핑이 전부 id 비교라 조인 없이 거른다. */
    @Column(name = "wholesaler_id", nullable = false, updatable = false)
    private Long wholesalerId;

    /** 한 봉투 = 한 소매처 — 포장 완료가 소매처 혼합을 거절한다 [M-3]. */
    @Column(name = "partner_id", nullable = false, updatable = false)
    private Long partnerId;

    /** 도매처별 연번 (D-075). 표시 코드(PKG-001)는 프론트 조립. */
    @Column(name = "outbound_number", nullable = false, updatable = false)
    private int outboundNumber;

    /** 도매처별 날짜별 연번 (D-076). NULL = 미확정. 표시 코드(JG-20260818-001)는 프론트 조립. */
    @Column(name = "statement_number")
    private Integer statementNumber;

    /** NULL = 포장완료 · 값 = 출고완료 (D-074). 채번에 쓴 시각을 그대로 저장한다. */
    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Outbound(Long wholesalerId, Long partnerId, int outboundNumber) {
        this.wholesalerId = wholesalerId;
        this.partnerId = partnerId;
        this.outboundNumber = outboundNumber;
    }

    /**
     * 출고 확정 — 재고가 실제로 줄어드는 유일한 지점의 문서 반영.
     * shippedAt 은 장끼 채번에 쓴 시각 그대로다 — 표시 코드와 날짜가 어긋나지 않게.
     */
    public void ship(OffsetDateTime shippedAt, int statementNumber) {
        this.shippedAt = shippedAt;
        this.statementNumber = statementNumber;
    }
}
