package com.ondo.wholesale.dashboard;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 대시보드 요약 재계산 (MUL-135).
 *
 * <p>요약은 진실이 아니다 — 진실은 원본 테이블이고, 여기서 언제든 다시 만들 수 있다.
 * 그래서 어긋났을 때의 복구 수단이자, 주기 갱신의 본체다.
 *
 * <p>도매처 하나씩 다시 계산한다. 전체를 한 번에 훑으면 그 자체가 무거워서, 큰 도매처가
 * 작은 도매처의 갱신을 막는다. 한 도매처의 재계산은 자기 데이터만 건드린다.
 *
 * <p>영업일 경계는 자정이 아니라 KST 낮 12시다 — {@link BusinessDay} 와 같은 기준을 쓴다.
 */
@Component
public class DashboardSummaryRefresher {

    /** 영업일 경계 — 12시 이전은 전날 영업일. SQL 에서도 같은 식을 쓴다. */
    private static final String BUSINESS_DAY =
            "((o.ordered_at AT TIME ZONE 'Asia/Seoul') - interval '12 hour')::date";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardSummaryRefresher(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 도매처 하나의 요약을 원본에서 다시 만든다.
     *
     * <p>네 표를 한 트랜잭션에서 갱신한다 — 절반만 새 값이면 화면의 숫자끼리 어긋난다.
     * 지우고 다시 넣는 방식이라, 원본에서 사라진 줄(미송 해소 등)도 같이 정리된다.
     */
    @Transactional
    public void refresh(long wholesalerId) {
        Map<String, Object> params = Map.of("wholesalerId", wholesalerId);

        refreshDaily(params);
        refreshNewCount(params);
        refreshBackorderSku(params);
        refreshPackingQueue(params);
    }

    private void refreshDaily(Map<String, Object> params) {
        jdbc.update("""
                delete from wholesale.dashboard_daily where wholesaler_id = :wholesalerId
                """, params);
        jdbc.update("""
                insert into wholesale.dashboard_daily
                    (wholesaler_id, business_day, order_count, order_amount, cancelled_count)
                select o.wholesaler_id,
                       %s,
                       count(distinct o.id),
                       coalesce(sum(oi.qty * oi.unit_price), 0),
                       count(distinct o.id) filter (where o.status = 'CANCELLED')
                from wholesale.orders o
                left join wholesale.order_item oi on oi.order_id = o.id
                where o.wholesaler_id = :wholesalerId
                group by 1, 2
                """.formatted(BUSINESS_DAY), params);
    }

    private void refreshNewCount(Map<String, Object> params) {
        jdbc.update("""
                insert into wholesale.dashboard_counter (wholesaler_id, new_count, refreshed_at)
                select :wholesalerId,
                       (select count(*) from wholesale.orders
                         where wholesaler_id = :wholesalerId and status = 'NEW'),
                       now()
                on conflict (wholesaler_id)
                do update set new_count = excluded.new_count, refreshed_at = excluded.refreshed_at
                """, params);
    }

    private void refreshBackorderSku(Map<String, Object> params) {
        jdbc.update("""
                delete from wholesale.dashboard_backorder_sku where wholesaler_id = :wholesalerId
                """, params);
        jdbc.update("""
                insert into wholesale.dashboard_backorder_sku
                    (wholesaler_id, variant_id, open_qty, oldest_created_at)
                select o.wholesaler_id, oi.variant_id,
                       sum(oi.qty - oi.allocated_qty),
                       min(b.created_at)
                from wholesale.backorder b
                join wholesale.order_item oi on oi.id = b.order_item_id
                join wholesale.orders o      on o.id = oi.order_id
                where b.status = 'OPEN' and o.wholesaler_id = :wholesalerId
                group by 1, 2
                having sum(oi.qty - oi.allocated_qty) > 0
                """, params);
    }

    private void refreshPackingQueue(Map<String, Object> params) {
        jdbc.update("""
                delete from wholesale.dashboard_packing_queue where wholesaler_id = :wholesalerId
                """, params);
        jdbc.update("""
                insert into wholesale.dashboard_packing_queue
                    (wholesaler_id, retailer_id, receive_method, qty)
                select o.wholesaler_id, pt.retailer_id, o.receive_method, sum(pi.qty)
                from wholesale.packing_item pi
                join wholesale.packing pk on pk.id = pi.packing_id
                join wholesale.orders o   on o.id = pk.order_id
                join wholesale.partner pt on pt.id = o.partner_id
                where o.wholesaler_id = :wholesalerId
                  and pk.status = 'READY' and pk.outbound_id is null
                  and pi.deleted_at is null
                group by 1, 2, 3
                """, params);
    }
}
