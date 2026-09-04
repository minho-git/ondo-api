package com.ondo.wholesale.order.service;

import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.order.SettlementStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 목록 화면의 배치 집계 조회 (MUL-47).
 *
 * <p>페이지에 실린 주문 id 묶음으로 한 번씩만 묻는다 — 주문마다 되묻는 N+1 이 없다.
 * 정산 값은 receivable_ledger 의 delta 합으로 파생한다. 출고·정산 티켓이 원장을 쓰기
 * 전에는 원장이 비어 있으므로 모든 주문이 UNPAID·미수 0 으로 내려간다.
 */
@Component
public class OrderSummaryReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 목록 행 머리의 "상품명 (색상) 외 N건" 재료. 첫 라인 = 라인 id 최소. */
    public record FirstLine(String productName, String colorName, int additionalCount) {
    }

    /** 파생 상태·버튼·금액의 재료가 되는 라인 합계. */
    public record QtySums(int totalQty, int allocatedSum, int shippedSum, int orderAmount) {
    }

    /** 주문별 정산 파생값. outstandingAmount 는 정산 탭 미수 잔액이자 배분 입력칸 상한. */
    public record Settlement(int outstandingAmount, SettlementStatus status) {
    }

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

    /** 요청한 모든 id 에 값을 채운다 — 원장이 없는 주문은 UNPAID·0. */
    public Map<Long, Settlement> settlements(List<Long> orderIds) {
        Map<Long, Settlement> result = new HashMap<>();
        for (Long id : orderIds) {
            result.put(id, new Settlement(0, SettlementStatus.UNPAID));
        }
        if (orderIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select order_id, sum(delta) as balance,
                       count(*) filter (where entry_type = 'PAYMENT') as payments
                from wholesale.receivable_ledger
                where order_id in (:ids)
                group by order_id
                """, Map.of("ids", orderIds), rs -> {
            result.put(rs.getLong("order_id"),
                    settlement(rs.getLong("balance"), rs.getLong("payments")));
        });
        return result;
    }

    /** settlementStatus 필터 — 원장 스캔이라 Specification 대신 id 를 골라 idIn 으로 조합한다. */
    public List<Long> orderIdsBySettlement(Long wholesalerId, SettlementStatus status) {
        String predicate = switch (status) {
            case UNPAID -> "coalesce(l.payments, 0) = 0";
            case PARTIALLY_SETTLED -> "coalesce(l.payments, 0) > 0 and coalesce(l.balance, 0) > 0";
            case SETTLED -> "coalesce(l.payments, 0) > 0 and coalesce(l.balance, 0) = 0";
        };
        return jdbc.queryForList("""
                select o.id
                from wholesale.orders o
                left join (select order_id, sum(delta) as balance,
                                  count(*) filter (where entry_type = 'PAYMENT') as payments
                           from wholesale.receivable_ledger
                           group by order_id) l on l.order_id = o.id
                where o.wholesaler_id = :wholesalerId and %s
                """.formatted(predicate), Map.of("wholesalerId", wholesalerId), Long.class);
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
            params.addValue("from", from.atStartOfDay(KST).toOffsetDateTime());
        }
        if (to != null) {
            where.append(" and o.ordered_at < :toNext");
            params.addValue("toNext", to.plusDays(1).atStartOfDay(KST).toOffsetDateTime());
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

    private static Settlement settlement(long balance, long payments) {
        SettlementStatus status = (payments == 0) ? SettlementStatus.UNPAID
                : (balance > 0) ? SettlementStatus.PARTIALLY_SETTLED
                : SettlementStatus.SETTLED;
        return new Settlement((int) balance, status);
    }
}
