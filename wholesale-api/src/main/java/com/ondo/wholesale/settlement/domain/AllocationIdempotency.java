package com.ondo.wholesale.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 선수금으로 정산 멱등 기록 (MUL-125) — 입금({@link PaymentIdempotency})과 같은 꼴. 만든 입금이 없어
 * 가리킬 id 가 없고, 응답 본문만 저장한다. 키는 입금 멱등과 같은 (도매처, 키) 복합키를 쓴다.
 */
@Entity
@Table(name = "allocation_idempotency", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllocationIdempotency {

    @EmbeddedId
    private PaymentIdempotency.Key key;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    /** 첫 201 의 data 페이로드 JSON — replay 는 이걸 그대로 내린다. */
    @Column(name = "response_body", nullable = false, updatable = false)
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AllocationIdempotency(Long wholesalerId, String idempotencyKey, String requestHash, String responseBody) {
        this.key = new PaymentIdempotency.Key(wholesalerId, idempotencyKey);
        this.requestHash = requestHash;
        this.responseBody = responseBody;
    }
}
