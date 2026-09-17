package com.ondo.wholesale.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 입금 등록 멱등 기록 (MUL-124) — 입고({@code InboundIdempotency})와 같은 꼴.
 * 응답의 ledgerBalance 가 시점값이라 재계산이 아니라 첫 응답 본문을 저장해 두고 내린다.
 */
@Entity
@Table(name = "payment_idempotency", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentIdempotency {

    @EmbeddedId
    private Key key;

    /** 고정 필드 순서 직렬화 SHA-256 hex. */
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    /** 첫 201 의 data 페이로드 JSON — replay 는 이걸 그대로 내린다. */
    @Column(name = "response_body", nullable = false, updatable = false)
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PaymentIdempotency(Long wholesalerId, String idempotencyKey, String requestHash,
                              Long paymentId, String responseBody) {
        this.key = new Key(wholesalerId, idempotencyKey);
        this.requestHash = requestHash;
        this.paymentId = paymentId;
        this.responseBody = responseBody;
    }

    /** 복합 PK (wholesaler_id, idempotency_key) — 키는 도매처 안에서만 유일하면 된다. */
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "wholesaler_id", nullable = false)
        private Long wholesalerId;

        @Column(name = "idempotency_key", nullable = false, length = 64)
        private String idempotencyKey;
    }
}
