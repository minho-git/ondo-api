package com.ondo.wholesale.order.domain;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.order.ReceiveBy;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주문. 라인({@link OrderItem})을 거느리는 애그리거트 뿌리다 (MUL-47).
 *
 * <p>partnerId·wholesalerId 는 연관 없이 Long 으로만 든다 — 스코핑·조회가 전부 id 비교다.
 * 미송({@link Backorder})·포장({@link Packing})은 이 애그리거트 밖이라 매핑하지 않는다.
 *
 * <p>retail_order_id·agent_name·agent_phone 은 소매 접수(MUL-98)가 쓰기 시작하면서 더했다.
 * 도매 화면에서 직접 넣은 주문은 셋 다 null 이다 — 소매를 안 거쳤으니 소매 주문서가 없고,
 * 수령인도 그때 정하지 않는다.
 */
@Entity
@Table(name = "orders", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 도매처별 연번 (D-064). 표시 코드(ORD-001)는 프론트 조립. */
    @Column(name = "order_number", nullable = false, updatable = false)
    private int orderNumber;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private Long partnerId;

    @Column(name = "wholesaler_id", nullable = false, updatable = false)
    private Long wholesalerId;

    /**
     * 소매 통합 주문서 id. 논리 참조(FK 없음, D-051) — DB 가 갈라져 있다.
     *
     * <p>도매 화면에서 직접 넣은 주문은 null 이다. 멱등성도 이 값으로 잡는다 —
     * UNIQUE(retail_order_id, wholesaler_id) 라 같은 주문서를 두 번 접수하면 걸린다.
     */
    @Column(name = "retail_order_id", updatable = false)
    private Long retailOrderId;

    /** 사입삼촌. 장끼에 수령인으로 찍힌다. 직접 수령이면 null (U-11). */
    @Column(name = "agent_name", length = 50)
    private String agentName;

    @Column(name = "agent_phone", length = 20)
    private String agentPhone;

    /** 저장 상태 3값. 출고 진행도는 파생이라 여기 없다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;

    /** 주문 시점의 결제 약속. 실제 입금 수단과 어긋날 수 있다 (D-090). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_term", nullable = false, length = 20)
    private PaymentMethod paymentTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "receive_method", nullable = false, length = 20)
    private ReceiveBy receiveMethod;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private OffsetDateTime orderedAt;

    /** CONFIRMED 전이 시각. */
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItem> items = new ArrayList<>();

    @Builder
    private Order(int orderNumber, Long partnerId, Long wholesalerId, Long retailOrderId,
                  PaymentMethod paymentTerm, ReceiveBy receiveMethod,
                  String agentName, String agentPhone, OffsetDateTime orderedAt) {
        this.orderNumber = orderNumber;
        this.partnerId = partnerId;
        this.wholesalerId = wholesalerId;
        this.retailOrderId = retailOrderId;
        this.paymentTerm = paymentTerm;
        this.receiveMethod = receiveMethod;
        this.agentName = agentName;
        this.agentPhone = agentPhone;
        this.orderedAt = orderedAt;
    }

    /** 확정 전이. NEW 인지 검증은 OrderCommandService 가 끝낸 뒤다. */
    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = OffsetDateTime.now();
    }

    /** 취소 전이. NEW 전용 — 확정 주문의 409 는 OrderCommandService 가 막는다. */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    /** 라인 한 건을 붙인다. 양쪽 참조를 함께 세운다. */
    public OrderItem addItem(Long variantId, int qty, int unitPrice) {
        OrderItem item = new OrderItem(this, variantId, qty, unitPrice);
        items.add(item);
        return item;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
