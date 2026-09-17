package com.ondo.wholesale.settlement.domain;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.settlement.PaidBy;
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
 * 입금 한 건 (MUL-124) — 통장에 찍힌 입금을 사장이 옮겨 적은 것. 금액은 항상 양수고,
 * 원장에는 이 금액의 음수로 적힌다. 잘못 넣은 입금은 지우지 않고 무효 표시만 한다(MUL-127).
 */
@Entity
@Table(name = "payment", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private Long partnerId;

    /** 비정규화 — 스코핑이 전부 id 비교라 조인 없이 거른다. */
    @Column(name = "wholesaler_id", nullable = false, updatable = false)
    private Long wholesalerId;

    /** Idempotency-Key 그대로. 연타·재시도가 입금을 두 번 만들지 못하게 하는 마지막 방어선. */
    @Column(name = "request_id", nullable = false, updatable = false, length = 64)
    private String requestId;

    @Column(nullable = false, updatable = false)
    private long amount;

    /** 누구 손으로 왔나 — 수단(method)과 다른 축. 사입삼촌 대납은 AGENT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "paid_by", nullable = false, updatable = false, length = 20)
    private PaidBy paidBy;

    /** 통장에 찍힌 표기 그대로. 계약에 아직 입력 칸이 없다. */
    @Column(name = "payer_name", updatable = false, length = 50)
    private String payerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private OffsetDateTime paidAt;

    @Column(updatable = false, length = 255)
    private String memo;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", length = 200)
    private String voidReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Payment(Long partnerId, Long wholesalerId, String requestId, long amount, PaidBy paidBy,
                    PaymentMethod method, OffsetDateTime paidAt, String memo) {
        this.partnerId = partnerId;
        this.wholesalerId = wholesalerId;
        this.requestId = requestId;
        this.amount = amount;
        this.paidBy = paidBy;
        this.method = method;
        this.paidAt = paidAt;
        this.memo = memo;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    /** 무효 표시 (MUL-127) — 행을 지우거나 금액을 고치지 않는다. 원장 반대 줄은 서비스가 원장 쓰기로 적는다. */
    public void voidWith(String reason, OffsetDateTime at) {
        this.voidReason = reason;
        this.voidedAt = at;
    }
}
