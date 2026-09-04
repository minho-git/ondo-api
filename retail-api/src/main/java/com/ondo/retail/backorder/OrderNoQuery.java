package com.ondo.retail.backorder;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 주문서 id 로 화면에 찍히는 주문번호를 찾는다 (MUL-97).
 *
 * <p>미송은 도매가 주는데 주문번호는 소매 것이라, 응답을 완성하려면 소매 DB 를 한 번 본다.
 * 도매에는 이 번호가 없다 — 도매의 {@code orders.order_number} 는 도매처별 연번이라
 * 아예 다른 번호다.
 *
 * <p><b>엔티티를 안 만든다.</b> 주문 실 연동(MUL-98)이 {@code order_group} 을 제대로 다룰
 * 텐데, 그 전에 읽기 두 칸짜리 엔티티를 먼저 박아두면 그쪽 설계를 미리 못 박는 꼴이 된다.
 * 여기서 필요한 건 {@code id → order_no} 하나뿐이다.
 */
@Repository
@RequiredArgsConstructor
public class OrderNoQuery {

    private final JdbcClient jdbc;

    /**
     * @param retailerId 세션에서 꺼낸 값. <b>조건에 같이 건다.</b> 도매가 어떤 이유로든
     *                   남의 줄을 섞어 보내도 주문번호까지는 새어 나가지 않는다
     * @return 찾은 것만 담긴다. 없는 id 는 키가 없다
     */
    public Map<Long, String> byOrderIds(long retailerId, Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        return jdbc.sql("""
                        SELECT id, order_no
                        FROM retail.order_group
                        WHERE id IN (:ids) AND retailer_id = :retailerId
                        """)
                .param("ids", orderIds)
                .param("retailerId", retailerId)
                .query((rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("order_no")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
