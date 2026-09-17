package com.ondo.wholesale.order.service;

import com.ondo.wholesale.common.time.KstDays;
import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.SettlementStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 목록 화면의 배치 집계 조회 (MUL-47).
 *
 * <p>페이지에 실린 주문 id 묶음으로 한 번씩만 묻는다 — 주문마다 되묻는 N+1 이 없다.
 * 정산 값은 출고로 생긴 미수(원장 OUTBOUND 줄)와 이 주문에 붙은 배분(payment_allocation)으로 파생한다
 * (MUL-126). 입금 줄은 주문을 가리키지 않으므로 원장을 주문별로 더해서는 받은 돈이 안 잡힌다.
 */
@Component
public class OrderSummaryReader {

    /** 목록 행 머리의 "상품명 (색상) 외 N건" 재료. 첫 라인 = 라인 id 최소. */
    public record FirstLine(String productName, String colorName, int additionalCount) {
    }

    /** 파생 상태·버튼·금액의 재료가 되는 라인 합계. */
    public record QtySums(int totalQty, int allocatedSum, int shippedSum, int orderAmount) {
    }

    /**
     * 주문별 정산 파생값. shippedAmount 는 출고로 생긴 미수, outstandingAmount 는 거기서 붙은 돈을 뺀 값 —
     * 정산 탭 미수 잔액이자 배분 입력칸 상한이다. 출고 전 주문은 둘 다 0 이고 UNPAID 다.
     */
    public record Settlement(int shippedAmount, int outstandingAmount, SettlementStatus status) {
    }

    /** 주문별 나간 금액 · 붙은 돈. 배분 규칙(AllocationPlanner)과 같은 정의다 — 취소된 배분 · 무효 입금의 배분 제외. */
    private static final String SETTLEMENT_SOURCE = """
            select o.id as order_id,
                   coalesce(s.shipped, 0)   as shipped,
                   coalesce(a.allocated, 0) as allocated
            from wholesale.orders o
            left join (select order_id, sum(delta) as shipped
                       from wholesale.receivable_ledger
                       where entry_type = 'OUTBOUND'
                       group by order_id) s on s.order_id = o.id
            left join (select pa.order_id, sum(pa.amount) as allocated
                       from wholesale.payment_allocation pa
                       join wholesale.payment p on p.id = pa.payment_id
                       where pa.cancelled_at is null and p.voided_at is null
                       group by pa.order_id) a on a.order_id = o.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OrderSummaryReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, FirstLine> firstLines(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FirstLine> result = new HashMap<>();
        jdbc.query("""
                select oi.order_id,
                       (array_agg(p.name order by oi.id))[1] as product_name,
                       (array_agg(c.name order by oi.id))[1] as color_name,
                       count(*) - 1                          as additional_count
                from wholesale.order_item oi
                join wholesale.variant v       on v.id = oi.variant_id
                join wholesale.product p       on p.id = v.product_id
                join wholesale.color_option co on co.id = v.color_option_id
                join common.color c            on c.id = co.color_id
                where oi.order_id in (:ids)
                group by oi.order_id
                """, Map.of("ids", orderIds), rs -> {
            result.put(rs.getLong("order_id"), new FirstLine(
                    rs.getString("product_name"), rs.getString("color_name"),
                    rs.getInt("additional_count")));
        });
        return result;
    }

    public Map<Long, QtySums> qtySums(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, QtySums> result = new HashMap<>();
        jdbc.query("""
                select order_id, sum(qty) as total, sum(allocated_qty) as allocated,
                       sum(shipped_qty) as shipped, sum(qty * unit_price) as amount
                from wholesale.order_item
                where order_id in (:ids)
                group by order_id
                """, Map.of("ids", orderIds), rs -> {
            result.put(rs.getLong("order_id"), new QtySums(
                    rs.getInt("total"), rs.getInt("allocated"),
                    rs.getInt("shipped"), rs.getInt("amount")));
        });
        return result;
    }

    /** 요청한 모든 id 에 값을 채운다 — 출고도 배분도 없는 주문은 UNPAID·0. */
    public Map<Long, Settlement> settlements(List<Long> orderIds) {
        Map<Long, Settlement> result = new HashMap<>();
        for (Long id : orderIds) {
            result.put(id, new Settlement(0, 0, SettlementStatus.UNPAID));
        }
        if (orderIds.isEmpty()) {
            return result;
        }
        jdbc.query(SETTLEMENT_SOURCE + " where o.id in (:ids)", Map.of("ids", orderIds), rs -> {
            result.put(rs.getLong("order_id"), settlement(rs.getLong("shipped"), rs.getLong("allocated")));
        });
        return result;
    }

    /** settlementStatus 필터 — 파생 값이라 Specification 대신 id 를 골라 idIn 으로 조합한다. */
    public List<Long> orderIdsBySettlement(Long wholesalerId, SettlementStatus status) {
        String predicate = switch (status) {
            case UNPAID -> "t.allocated = 0";
            case PARTIALLY_SETTLED -> "t.allocated > 0 and t.shipped > t.allocated";
            case SETTLED -> "t.allocated > 0 and t.shipped <= t.allocated";
        };
        return jdbc.queryForList("""
                select t.order_id from (%s where o.wholesaler_id = :wholesalerId) t
                where %s
                """.formatted(SETTLEMENT_SOURCE, predicate), Map.of("wholesalerId", wholesalerId), Long.class);
    }

    /**
     * 상태 칩 건수 — 파생 버킷 group-by 한 방. 목록과 같은 q·기간 조건을 받는다.
     * ALL 을 포함한 6키 전부 채워서 돌려준다 (없는 버킷은 0).
     */
    public Map<OrderFilterKey, Long> chipCounts(Long wholesalerId, String q,
                                                LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("wholesalerId", wholesalerId);
        StringBuilder where = new StringBuilder("o.wholesaler_id = :wholesalerId");
        if (q != null && !q.isBlank()) {
            where.append("""
                     and (exists (select 1 from wholesale.partner pt
                                  where pt.id = o.partner_id and pt.retailer_name ilike :q)
                       or exists (select 1 from wholesale.order_item oi
                                  join wholesale.variant v on v.id = oi.variant_id
                                  join wholesale.product p on p.id = v.product_id
                                  where oi.order_id = o.id and p.name ilike :q))
                    """);
            params.addValue("q", "%" + q + "%");
        }
        if (from != null) {
            where.append(" and o.ordered_at >= :from");
            params.addValue("from", KstDays.start(from));
        }
        if (to != null) {
            where.append(" and o.ordered_at < :toNext");
            params.addValue("toNext", KstDays.startOfNext(to));
        }

        Map<OrderFilterKey, Long> result = new EnumMap<>(OrderFilterKey.class);
        for (OrderFilterKey key : OrderFilterKey.values()) {
            result.put(key, 0L);
        }
        jdbc.query("""
                select case when o.status = 'NEW' then 'NEW'
                            when o.status = 'CANCELLED' then 'CANCELLED'
                            when coalesce(s.shipped, 0) = 0 then 'CONFIRMED'
                            when s.shipped < s.total then 'PARTIALLY_SHIPPED'
                            else 'SHIPPED' end as bucket,
                       count(*) as cnt
                from wholesale.orders o
                left join (select order_id, sum(qty) as total, sum(shipped_qty) as shipped
                           from wholesale.order_item
                           group by order_id) s on s.order_id = o.id
                where %s
                group by bucket
                """.formatted(where), params, rs -> {
            long count = rs.getLong("cnt");
            result.put(OrderFilterKey.valueOf(rs.getString("bucket")), count);
            result.merge(OrderFilterKey.ALL, count, Long::sum);
        });
        return result;
    }

    /** 붙은 돈이 없으면 UNPAID, 나간 금액보다 적으면 PARTIALLY_SETTLED, 다 붙었으면 SETTLED. */
    private static Settlement settlement(long shipped, long allocated) {
        SettlementStatus status = (allocated == 0) ? SettlementStatus.UNPAID
                : (shipped > allocated) ? SettlementStatus.PARTIALLY_SETTLED
                : SettlementStatus.SETTLED;
        return new Settlement(Math.toIntExact(shipped), Math.toIntExact(Math.max(shipped - allocated, 0)), status);
    }
}
