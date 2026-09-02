package com.ondo.retail.retailer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 승인 상태가 바뀔 때마다 한 줄씩 쌓이는 이력.
 *
 * <p>거절 사유는 <b>여기에만 있다.</b> {@code retailer} 에는 지금 상태만 있어서
 * "왜 거절됐는지" 를 알려면 이 표의 마지막 줄을 봐야 한다.
 *
 * <p>쓰는 건 운영자 화면이고 <b>소매 API 는 읽기만 한다.</b> 그래서 저장 메서드가 없다.
 */
@Entity
@Table(name = "approval_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retailer_id", nullable = false)
    private Long retailerId;

    /** 최초 신청이면 null 이다 — 그전 상태가 없다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ApprovalStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ApprovalStatus toStatus;

    /** 거절 사유. 승인일 때는 비어 있다. */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * 누가 바꿨는지. {@code SYSTEM} 이거나 <b>운영자 이메일</b>이다.
     *
     * <p><b>이 값을 그대로 응답에 담지 않는다.</b> 소매처가 운영자 이메일을 알 이유가 없다.
     * 명세대로 "운영자" 로 고정해서 내린다.
     */
    @Column(name = "actor", nullable = false, length = 50)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
