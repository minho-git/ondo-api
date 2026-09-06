package com.ondo.retail.order.domain;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 소매 통합 주문서 (MUL-98).
 *
 * <p>도매처가 여럿이어도 소매는 주문서 하나로 받는다. 접수할 때 도매처별로 잘려
 * 도매에 N 번 나가고, 도매 쪽 주문은 이 id 를 {@code retail_order_id} 로 들고 있다 —
 * DB 가 갈라져 있어 FK 는 없다.
 *
 * <p>도매처별 주문 내용은 여기 없다. 그건 도매 DB 에 있고 필요할 때 물어본다.
 * 소매가 들고 있는 건 <b>주문서라는 사실과 그 번호</b>까지다.
 *
 * <p>{@code totalAmount} 는 접수된 것만 합한 값이고 주문 시점에 고정한다. 취소가
 * 나도 안 바꾼다 — 경계 너머 집계라 그때 값을 남겨두는 게 맞다(V1 주석).
 */
@Entity
@Table(name = "order_group", schema = "retail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retailer_id", nullable = false, updatable = false)
    private Long retailerId;

    /** 브라우저가 만든 번호. 연타를 막는다 (M-7 · 숙제 2번). */
    @Column(name = "request_id", nullable = false, length = 64, updatable = false)
    private String requestId;

    /** 화면에 보여주는 번호. {@code 20260902-1420-0088} 모양이다. */
    @Column(name = "order_no", nullable = false, length = 30, updatable = false)
    private String orderNo;

    /** 사입삼촌. 도매처가 여럿이어도 사람은 하나다. 직접 수령이면 null. */
    @Column(name = "agent_name", length = 50, updatable = false)
    private String agentName;

    @Column(name = "agent_phone", length = 20, updatable = false)
    private String agentPhone;

    /** 접수 성공분 합계. 도매를 다 부른 뒤에 채운다. */
    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    /**
     * 한 곳이라도 접수됐는지 (MUL-98).
     *
     * <p>도매를 부르려면 이 행의 id 가 있어야 해서 주문서를 먼저 만든다. 전부 거절되면
     * 주문이 하나도 안 달린 껍데기가 남는데, 지우지 않고 {@code FAILED} 로 둔다 —
     * 왜 실패했는지 남고 멱등키도 살아남는다. 내역에는 {@code ACCEPTED} 만 보인다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderGroupStatus status = OrderGroupStatus.ACCEPTED;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private OffsetDateTime orderedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private OrderGroup(Long retailerId, String requestId, String orderNo,
                       String agentName, String agentPhone, OffsetDateTime orderedAt) {
        this.retailerId = retailerId;
        this.requestId = requestId;
        this.orderNo = orderNo;
        this.agentName = agentName;
        this.agentPhone = agentPhone;
        this.orderedAt = orderedAt;
        this.totalAmount = 0;
    }

    /** 도매를 다 부른 뒤 결과를 반영한다. 한 곳도 못 받았으면 실패로 남긴다. */
    public void settle(long acceptedAmount, boolean anyAccepted) {
        this.totalAmount = acceptedAmount;
        this.status = anyAccepted ? OrderGroupStatus.ACCEPTED : OrderGroupStatus.FAILED;
    }
}
