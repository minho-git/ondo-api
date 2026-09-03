package com.ondo.wholesale.product.service;

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
