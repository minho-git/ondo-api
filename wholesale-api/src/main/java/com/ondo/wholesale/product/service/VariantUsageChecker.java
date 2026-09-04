package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * variant 가 주문·미송 쪽에 물려 있는지 보는 조회 창구.
 *
 * <p>backorder·order_item 은 주문(MUL-47)·미송(MUL-48) 티켓의 애그리거트라 여기서
 * 엔티티를 만들지 않고 SQL 로만 읽는다 — 그 티켓들이 규칙을 바꾸면 이 클래스만 고친다.
 *
 * <p>backorderQty 는 "OPEN 미송의 원래 수량 합" 근사치다. 부분 해소 반영(출고 차감)은
 * 미송 티켓에서 정밀화한다.
 */
@Component
@RequiredArgsConstructor
public class VariantUsageChecker {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 삭제 후보 variant 들이 지워도 되는 상태인지 일괄 검사한다. 하나라도 걸리면 예외 —
     * 호출자는 이 검사를 통과했을 때만 soft delete 를 실행해 부분 삭제를 막는다.
     *
     * <p>검사 순서는 계약(@Operation) 나열 순서로 고정한다 — 여러 조건에 동시에 걸려도
     * 응답 코드가 흔들리지 않는다:
     * <ol>
     *   <li>variant.stock_qty &gt; 0 → {@code VARIANT_HAS_STOCK}</li>
     *   <li>variant.reserved_qty &gt; 0 → {@code VARIANT_ALLOCATED}</li>
     *   <li>OPEN 미송 존재 (backorder ⋈ order_item) → {@code VARIANT_HAS_BACKORDER}</li>
     *   <li>NEW·CONFIRMED 주문에 포함 (order_item ⋈ orders) → {@code VARIANT_IN_PENDING_ORDER}</li>
     * </ol>
     * 주문(MUL-47)·미송(MUL-48) 티켓이 상태 규칙을 바꾸면 이 SQL 만 고친다.
     */
    public void ensureDeletable(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return;
        }
        Map<String, ?> params = Map.of("ids", variantIds);
        if (exists("select 1 from wholesale.variant where id in (:ids) and stock_qty > 0", params)) {
            throw new ApiException(ErrorCode.VARIANT_HAS_STOCK);
        }
        if (exists("select 1 from wholesale.variant where id in (:ids) and reserved_qty > 0", params)) {
            throw new ApiException(ErrorCode.VARIANT_ALLOCATED);
        }
        if (exists("""
                select 1 from wholesale.backorder b
                join wholesale.order_item oi on oi.id = b.order_item_id
                where b.status = 'OPEN' and oi.variant_id in (:ids)
                """, params)) {
            throw new ApiException(ErrorCode.VARIANT_HAS_BACKORDER);
        }
        if (exists("""
                select 1 from wholesale.order_item oi
                join wholesale.orders o on o.id = oi.order_id
                where o.status in ('NEW', 'CONFIRMED') and oi.variant_id in (:ids)
                """, params)) {
            throw new ApiException(ErrorCode.VARIANT_IN_PENDING_ORDER);
        }
    }

    private boolean exists(String sql, Map<String, ?> params) {
        return !jdbc.queryForList(sql + " limit 1", params, Integer.class).isEmpty();
    }

    /** variant id → 미해소 미송 수량. 미송 없는 variant 는 키 자체가 없다. */
    public Map<Long, Integer> backorderQtyByVariant(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query("""
                select oi.variant_id, sum(b.qty) as qty
                  from wholesale.backorder b
                  join wholesale.order_item oi on oi.id = b.order_item_id
                 where b.status = 'OPEN' and oi.variant_id in (:ids)
                 group by oi.variant_id
                """, Map.of("ids", variantIds),
                rs -> {
                    result.put(rs.getLong("variant_id"), rs.getInt("qty"));
                });
        return result;
    }
}
