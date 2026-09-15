package com.ondo.wholesale.dashboard;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ondo.wholesale.order.ReceiveBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
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

    /** 포장 대기 — byReceive 는 수령방식별 소매처 수, 두 방식이 섞인 소매처는 양쪽에 잡힌다. */
    public record PackingAgg(int retailerCount, int qty, Map<ReceiveBy, Integer> byReceive) {}

    /** 출고 확정 안 한 봉투 — staleCount 는 이번 영업일 시작 전에 포장(생성)된 봉투 수. */
    public record OutboundAgg(int notShippedCount, int staleCount) {}

    /** 오늘(영업일) 출고 — 봉투 수와 담긴 장수. */
    public record TodayShippedAgg(int count, int qty) {}

    /** 미송 — 잔여 장수는 Σ(주문수량 − 배분수량), 입고일 판정은 KST 달력 날짜 기준. */
    public record BackorderAgg(int skuCount, int qty, int overdueSkuCount, int noDateSkuCount) {}

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

    /** 대기 술어는 {@code PackingQueueQueryService.queueWhere}와 동일해야 한다 (D-073). */
    public PackingAgg packing(long wholesalerId) {
        String joins = """
                from wholesale.packing_item pi
                join wholesale.packing pk on pk.id = pi.packing_id
                join wholesale.orders o   on o.id = pk.order_id
                join wholesale.partner pt on pt.id = o.partner_id
                where o.wholesaler_id = :wholesalerId
                  and pk.status = 'READY' and pk.outbound_id is null
                  and pi.deleted_at is null
                """;
        Map<String, Long> params = Map.of("wholesalerId", wholesalerId);
        PackingTotals totals = jdbc.queryForObject("""
                select count(distinct pt.retailer_id) as retailer_count,
                       coalesce(sum(pi.qty), 0)       as qty
                """ + joins, params,
                (rs, rowNum) -> new PackingTotals(rs.getInt("retailer_count"), rs.getInt("qty")));
        Map<ReceiveBy, Integer> byReceive = new EnumMap<>(ReceiveBy.class);
        for (ReceiveBy receiveBy : ReceiveBy.values()) {
            byReceive.put(receiveBy, 0);
        }
        jdbc.query("""
                select o.receive_method, count(distinct pt.retailer_id) as cnt
                """ + joins + " group by o.receive_method", params,
                rs -> {
                    byReceive.put(ReceiveBy.valueOf(rs.getString("receive_method")), rs.getInt("cnt"));
                });
        return new PackingAgg(totals.retailerCount(), totals.qty(), byReceive);
    }

    private record PackingTotals(int retailerCount, int qty) {}

    /** 봉투 상태는 컬럼이 아니라 {@code shipped_at} NULL 여부다 (D-074). 포장 시각 = 봉투 생성 시각. */
    public OutboundAgg outbound(long wholesalerId, OffsetDateTime businessDayStart) {
        return jdbc.queryForObject("""
                select count(*)                                              as not_shipped,
                       count(*) filter (where ob.created_at < :start)        as stale
                from wholesale.outbound ob
                where ob.wholesaler_id = :wholesalerId and ob.shipped_at is null
                """, Map.of("wholesalerId", wholesalerId, "start", businessDayStart),
                (rs, rowNum) -> new OutboundAgg(rs.getInt("not_shipped"), rs.getInt("stale")));
    }

    public TodayShippedAgg todayShipped(long wholesalerId, OffsetDateTime businessDayStart) {
        return jdbc.queryForObject("""
                select count(distinct ob.id)      as cnt,
                       coalesce(sum(pi.qty), 0)   as qty
                from wholesale.outbound ob
                left join wholesale.packing pk      on pk.outbound_id = ob.id
                left join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
                where ob.wholesaler_id = :wholesalerId and ob.shipped_at >= :start
                """, Map.of("wholesalerId", wholesalerId, "start", businessDayStart),
                (rs, rowNum) -> new TodayShippedAgg(rs.getInt("cnt"), rs.getInt("qty")));
    }

    /** 조인·잔여 식은 {@code BackorderQueryService.OPEN_BACKORDER_FROM}·skuList 와 동일해야 한다. */
    public BackorderAgg backorder(long wholesalerId, LocalDate todayKst) {
        return jdbc.queryForObject("""
                select count(distinct v.id)                                                     as sku_count,
                       coalesce(sum(oi.qty - oi.allocated_qty), 0)                              as qty,
                       count(distinct v.id) filter (where v.expected_inbound_date < :today)     as overdue,
                       count(distinct v.id) filter (where v.expected_inbound_date is null)      as no_date
                from wholesale.backorder b
                join wholesale.order_item oi on oi.id = b.order_item_id
                join wholesale.orders o      on o.id = oi.order_id
                join wholesale.variant v     on v.id = oi.variant_id
                where b.status = 'OPEN' and o.wholesaler_id = :wholesalerId
                """, Map.of("wholesalerId", wholesalerId, "today", todayKst),
                (rs, rowNum) -> new BackorderAgg(rs.getInt("sku_count"), rs.getInt("qty"),
                        rs.getInt("overdue"), rs.getInt("no_date")));
    }
}
