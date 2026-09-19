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
 * 대시보드 summary 의 집계 조회 (MUL-120 · MUL-135).
 *
 * <p>무거운 넷(오늘 주문 · 확정 대기 건수 · 포장 대기 · 미송)은 요약 표에서 읽고,
 * 가벼운 둘(출고 봉투 · 오늘 출고)은 원본에서 그대로 센다. 부분 인덱스가 있어
 * 0.05ms 안쪽이라 요약을 둘 이유가 없다.
 *
 * <p>요약을 도입한 이유 — 주문 300만 건(상가 1곳 1년치)에서 집계 6종 합계가
 * 인덱스를 걸어도 67ms 였다. 인덱스는 찾는 양을 줄이지만 세는 일은 그대로 남는다.
 * 요약을 읽으면 0.2ms 다. 대신 값이 최대 갱신 주기만큼 오래될 수 있다 —
 * 대시보드가 30초 폴링이라 화면에서 구분되지 않는다.
 *
 * <p>술어는 각 도메인의 원본 구현과 같아야 한다. 요약을 만드는 SQL 은
 * {@link DashboardSummaryRefresher} 에 있고, 그쪽이 원본 술어를 따른다.
 */
@Component
public class DashboardSummaryReader {

    /** 확정 기다리는 주문 — 건수는 요약에서, 가장 오래된 한 건은 인덱스로 찾는다. */
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

    /**
     * 건수는 요약에서 읽는다.
     *
     * <p>한 쿼리로 건수와 가장 오래된 주문을 같이 구하면 {@code count(*) over()} 때문에
     * {@code limit 1} 인데도 전체를 센다 — 주문 300만 건에서 5ms 였다. 떼어내면 0.04ms.
     */
    public NewOrdersAgg newOrders(long wholesalerId) {
        Integer count = jdbc.queryForObject("""
                select coalesce((select new_count from wholesale.dashboard_counter
                                  where wholesaler_id = :wholesalerId), 0)
                """, Map.of("wholesalerId", wholesalerId), Integer.class);

        List<Oldest> oldest = jdbc.query("""
                select o.ordered_at, pt.retailer_name
                from wholesale.orders o
                join wholesale.partner pt on pt.id = o.partner_id
                where o.wholesaler_id = :wholesalerId and o.status = 'NEW'
                order by o.ordered_at asc, o.id asc
                limit 1
                """, Map.of("wholesalerId", wholesalerId),
                (rs, rowNum) -> new Oldest(rs.getObject("ordered_at", OffsetDateTime.class),
                        rs.getString("retailer_name")));

        int safeCount = count == null ? 0 : count;
        return oldest.isEmpty()
                ? new NewOrdersAgg(safeCount, null, null)
                : new NewOrdersAgg(safeCount, oldest.getFirst().orderedAt(), oldest.getFirst().retailerName());
    }

    private record Oldest(OffsetDateTime orderedAt, String retailerName) {}

    /** 영업일 한 줄을 읽는다. 오늘 주문이 아직 없으면 행이 없고, 그때는 0 이다. */
    public TodayOrdersAgg todayOrders(long wholesalerId, LocalDate businessDay) {
        List<TodayOrdersAgg> rows = jdbc.query("""
                select order_count, order_amount, cancelled_count
                from wholesale.dashboard_daily
                where wholesaler_id = :wholesalerId and business_day = :businessDay
                """, Map.of("wholesalerId", wholesalerId, "businessDay", businessDay),
                (rs, rowNum) -> new TodayOrdersAgg(rs.getInt("order_count"),
                        (int) rs.getLong("order_amount"), rs.getInt("cancelled_count")));
        return rows.isEmpty() ? new TodayOrdersAgg(0, 0, 0) : rows.getFirst();
    }

    /** 소매처를 행으로 들고 있으므로 세는 대신 행을 읽는다. */
    public PackingAgg packing(long wholesalerId) {
        Map<ReceiveBy, Integer> byReceive = new EnumMap<>(ReceiveBy.class);
        for (ReceiveBy receiveBy : ReceiveBy.values()) {
            byReceive.put(receiveBy, 0);
        }
        jdbc.query("""
                select receive_method, count(distinct retailer_id) as cnt
                from wholesale.dashboard_packing_queue
                where wholesaler_id = :wholesalerId and qty > 0
                group by receive_method
                """, Map.of("wholesalerId", wholesalerId),
                rs -> {
                    byReceive.put(ReceiveBy.valueOf(rs.getString("receive_method")), rs.getInt("cnt"));
                });

        return jdbc.queryForObject("""
                select count(distinct retailer_id) as retailer_count,
                       coalesce(sum(qty), 0)       as qty
                from wholesale.dashboard_packing_queue
                where wholesaler_id = :wholesalerId and qty > 0
                """, Map.of("wholesalerId", wholesalerId),
                (rs, rowNum) -> new PackingAgg(rs.getInt("retailer_count"), rs.getInt("qty"), byReceive));
    }

    /** 봉투 상태는 컬럼이 아니라 {@code shipped_at} NULL 여부다 (D-074). 부분 인덱스로 충분하다. */
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

    /**
     * 미송 — SKU 별 잔여량은 요약에서 읽고, 입고일 판정만 variant 와 조인해 한다.
     *
     * <p>"입고일 지남"은 날짜가 바뀌면 저절로 변하는 값이라 세어 두지 않는다. 세어 두면
     * 매일 다시 계산하는 일이 생긴다.
     */
    public BackorderAgg backorder(long wholesalerId, LocalDate todayKst) {
        return jdbc.queryForObject("""
                select count(*)                                                                 as sku_count,
                       coalesce(sum(s.open_qty), 0)                                             as qty,
                       count(*) filter (where v.expected_inbound_date < :today)                 as overdue,
                       count(*) filter (where v.expected_inbound_date is null)                  as no_date
                from wholesale.dashboard_backorder_sku s
                join wholesale.variant v on v.id = s.variant_id
                where s.wholesaler_id = :wholesalerId and s.open_qty > 0
                """, Map.of("wholesalerId", wholesalerId, "today", todayKst),
                (rs, rowNum) -> new BackorderAgg(rs.getInt("sku_count"), rs.getInt("qty"),
                        rs.getInt("overdue"), rs.getInt("no_date")));
    }
}
