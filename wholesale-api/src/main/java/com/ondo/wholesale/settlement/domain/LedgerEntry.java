package com.ondo.wholesale.settlement.domain;

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

import java.time.OffsetDateTime;

/**
 * 미수 원장 한 행 (MUL-49) — APPEND-ONLY. 행을 지우거나 고치지 않으므로 모든 컬럼이
 * 불변이고 도메인 메서드가 없다. V1 {@code receivable_ledger} 테이블 그대로의 최소 매핑 —
 * 출고 확정이 OUTBOUND(+) 행을 적고, 입금·조회(정산 티켓)가 이 위에서 이어진다.
 *
 * <p>{@code balanceAfter}는 검산용 파생값이라 쓰는 쪽이 partner 행 락 아래서 계산해
 * 넣는다. {@code requestId}는 연타·재시도가 행을 두 번 만들지 못하게 하는 유니크 키다.
 */
@Entity
@Table(name = "receivable_ledger", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원장 조회의 축. */
    @Column(name = "partner_id", nullable = false, updatable = false)
    private Long partnerId;

    @Column(name = "request_id", nullable = false, updatable = false, length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 20)
    private ReceivableEntryType entryType;

    /** 부호 포함 — OUTBOUND(+) · PAYMENT(−) · ADJUST(±). */
    @Column(nullable = false, updatable = false)
    private long delta;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    /** 모든 행이 주문을 가리킨다 — 주문별 정산 파생(OrderSummaryReader)의 축. */
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    /** OUTBOUND 행만 값. */
    @Column(name = "outbound_id", updatable = false)
    private Long outboundId;

    /** PAYMENT 행 필수 — 정산 티켓이 채운다. */
    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    /** ADJUST 필수 — 정산 티켓이 채운다. */
    @Column(updatable = false, length = 200)
    private String memo;

    @Column(updatable = false, length = 50)
    private String actor;

    /** 미수 발생 시점 [X-1] — OUTBOUND 는 출고 확정 시각. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private LedgerEntry(Long partnerId, String requestId, ReceivableEntryType entryType,
                        long delta, long balanceAfter, Long orderId, Long outboundId,
                        Long paymentId, String memo, String actor, OffsetDateTime occurredAt) {
        this.partnerId = partnerId;
        this.requestId = requestId;
        this.entryType = entryType;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.orderId = orderId;
        this.outboundId = outboundId;
        this.paymentId = paymentId;
        this.memo = memo;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }
}
