package com.ondo.wholesale.order.domain;

import com.ondo.wholesale.order.PackingStatus;

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
 * 포장(봉투 1개 = 1행). 항목({@link PackingItem})을 거느리는 애그리거트 뿌리다 (MUL-47).
 *
 * <p>주문·출고는 이 애그리거트 밖이라 Long 으로만 든다. outboundId 가 NULL 이면 아직
 * 매장에 있다 — "포장 대기열"은 실체가 없고 {@code status=READY AND outbound_id IS NULL}
 * 조건이 전부다 (D-073).
 */
@Entity
@Table(name = "packing", schema = "wholesale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Packing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    /** NULL = 아직 매장. 값을 채우는 것은 출고 티켓이다. */
    @Column(name = "outbound_id")
    private Long outboundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PackingStatus status = PackingStatus.READY;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "packing", cascade = CascadeType.PERSIST)
    private List<PackingItem> items = new ArrayList<>();

    @Builder
    private Packing(Long orderId) {
        this.orderId = orderId;
    }

    /** 항목 한 건을 붙인다. backorderId 는 미송 배분분일 때만 값이 있다. */
    public PackingItem addItem(Long orderItemId, Long backorderId, Long allocationBatchId, int qty) {
        PackingItem item = new PackingItem(this, orderItemId, backorderId, allocationBatchId, qty);
        items.add(item);
        return item;
    }

    /**
     * 출고에 묶는 전이 (MUL-49) — READY 이고 아직 안 묶였을 때만. 409(PACKING_NOT_READY)
     * 매핑은 출고 생성 서비스가 하고, 여기는 잘못된 전이를 막는 마지막 그물이다.
     */
    public void pack(Long outboundId) {
        if (status != PackingStatus.READY || this.outboundId != null) {
            throw new IllegalStateException("READY 상태의 미출고 포장만 출고에 묶을 수 있다.");
        }
        this.status = PackingStatus.PACKED;
        this.outboundId = outboundId;
    }

    /**
     * 일부 항목만 출고로 나갈 때 포장을 쪼갠다 (MUL-49 · D-073) — 남는 쪽(this)의 id 가
     * 유지되어 대기열에 남고, 나가는 항목들이 새 포장으로 재부모화된다(항목 id 유지).
     * 새 포장의 저장은 호출부 몫이다.
     */
    public Packing splitOff(List<PackingItem> departingItems) {
        Packing departed = new Packing(this.orderId);
        for (PackingItem item : departingItems) {
            this.items.remove(item);
            item.moveTo(departed);
            departed.items.add(item);
        }
        return departed;
    }

    public List<PackingItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
