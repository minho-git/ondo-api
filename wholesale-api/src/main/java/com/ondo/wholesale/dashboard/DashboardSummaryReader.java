package com.ondo.wholesale.dashboard;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 summary 의 집계 조회 (MUL-120) — 화면 숫자마다 SQL 한 방.
 *
 * <p>술어는 각 도메인의 원본 구현과 같아야 한다 — 숫자가 목록 화면과 어긋나면
 * 대시보드를 믿을 수 없다. 원본: 주문 칩 {@code OrderSummaryReader.chipCounts},
 * 포장 대기 {@code PackingQueueQueryService.queueWhere}, 출고 {@code OutboundSpecs},
 * 미송 {@code BackorderQueryService.OPEN_BACKORDER_FROM}.
 */
@Component
public class DashboardSummaryReader {

    /** 확정 기다리는 주문 — 없으면 count 0 에 나머지는 null. */
    public record NewOrdersAgg(int count, OffsetDateTime oldestOrderedAt, String oldestRetailerName) {}

    /** 오늘(영업일) 주문 — 건수·금액은 취소 포함(주문 칩 ALL 과 같은 기준), 취소는 따로 센다. */
    public record TodayOrdersAgg(int count, int amount, int cancelled) {}

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardSummaryReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 술어는 주문 칩 NEW 버킷과 동일 — {@code status = 'NEW'}. */
    public NewOrdersAgg newOrders(long wholesalerId) {
        List<NewOrdersAgg> rows = jdbc.query("""
                select count(*) over() as cnt, o.ordered_at, pt.retailer_name
                from wholesale.orders o
                join wholesale.partner pt on pt.id = o.partner_id
                where o.wholesaler_id = :wholesalerId and o.status = 'NEW'
                order by o.ordered_at asc, o.id asc
                limit 1
                """, Map.of("wholesalerId", wholesalerId),
                (rs, rowNum) -> new NewOrdersAgg(rs.getInt("cnt"),
                        rs.getObject("ordered_at", OffsetDateTime.class),
                        rs.getString("retailer_name")));
        return rows.isEmpty() ? new NewOrdersAgg(0, null, null) : rows.getFirst();
    }

    /** 금액은 라인 스냅샷 합 — {@code OrderSummaryReader.qtySums}의 amount 와 같은 식. */
    public TodayOrdersAgg todayOrders(long wholesalerId, OffsetDateTime businessDayStart) {
        return jdbc.queryForObject("""
                select count(distinct o.id)                                        as cnt,
                       coalesce(sum(oi.qty * oi.unit_price), 0)                    as amount,
                       count(distinct o.id) filter (where o.status = 'CANCELLED')  as cancelled
                from wholesale.orders o
                left join wholesale.order_item oi on oi.order_id = o.id
                where o.wholesaler_id = :wholesalerId and o.ordered_at >= :start
                """, Map.of("wholesalerId", wholesalerId, "start", businessDayStart),
                (rs, rowNum) -> new TodayOrdersAgg(rs.getInt("cnt"), rs.getInt("amount"),
                        rs.getInt("cancelled")));
    }
}
