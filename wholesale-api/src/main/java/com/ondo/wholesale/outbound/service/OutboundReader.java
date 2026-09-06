package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.time.KstDays;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.outbound.OutboundStatusFilter;
import com.ondo.wholesale.outbound.dto.OutboundDetailResponse;
import com.ondo.wholesale.outbound.dto.OutboundItemResponse;
import com.ondo.wholesale.product.domain.Size;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 출고 화면의 jdbc 배치 집계 (MUL-49) — OrderSummaryReader 패턴.
 *
 * <p>봉투의 수량·품목 요약은 저장하지 않고 살아있는({@code deleted_at IS NULL}) 포장
 * 항목에서 파생한다. 소매처 헤더는 소매처 단위로 페이징해 같은 소매처가 페이지
 * 경계에서 쪼개지지 않는다.
 */
@Component
public class OutboundReader {

    /** 출고 탭 아코디언 헤더 한 행의 재료. */
    public record RetailerRow(long retailerId, String retailerName, int outboundCount,
                              int totalQty, OffsetDateTime lastCreatedAt, OffsetDateTime lastShippedAt) {
    }

    /** 소매처 단위 페이징 결과 — total 은 소매처 수다(봉투 수가 아니다). */
    public record RetailerPage(List<RetailerRow> rows, long total) {
    }

    /** 봉투 목록 한 행의 파생 요약 — 첫 품목명·SKU 종류 기준 외 N건·수량 합. */
    public record Summary(String firstProductName, int additionalItemCount,
                          int totalQty, ReceiveBy receiveBy) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public OutboundReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RetailerPage retailerPage(Long wholesalerId, OutboundListQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = outboundWhere(params, wholesalerId, query);
        long total = jdbc.queryForObject("""
                select count(distinct pt.id)
                from wholesale.outbound ob
                join wholesale.partner pt on pt.id = ob.partner_id
                where %s
                """.formatted(where), params, Long.class);

        params.addValue("limit", query.size());
        params.addValue("offset", (long) query.page() * query.size());
        List<RetailerRow> rows = new ArrayList<>();
        jdbc.query("""
                select pt.retailer_id, pt.retailer_name,
                       count(distinct ob.id) as outbound_count,
                       coalesce(sum(pi.qty), 0) as total_qty,
                       max(ob.created_at) as last_created_at,
                       max(ob.shipped_at) as last_shipped_at
                from wholesale.outbound ob
                join wholesale.partner pt on pt.id = ob.partner_id
                left join wholesale.packing pk on pk.outbound_id = ob.id
                left join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
                where %s
                group by pt.retailer_id, pt.retailer_name
                order by max(ob.created_at) desc, pt.retailer_id desc
                limit :limit offset :offset
                """.formatted(where), params, rs -> {
            rows.add(new RetailerRow(
                    rs.getLong("retailer_id"), rs.getString("retailer_name"),
                    rs.getInt("outbound_count"), rs.getInt("total_qty"),
                    rs.getObject("last_created_at", OffsetDateTime.class),
                    rs.getObject("last_shipped_at", OffsetDateTime.class)));
        });
        return new RetailerPage(rows, total);
    }

    /** 페이지에 실린 봉투 id 묶음으로 한 번만 묻는다 — 봉투마다 되묻는 N+1 이 없다. */
    public Map<Long, Summary> summaries(List<Long> outboundIds) {
        if (outboundIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Summary> result = new HashMap<>();
        jdbc.query("""
                select pk.outbound_id,
                       (array_agg(p.name order by pi.id))[1] as first_name,
                       count(distinct oi.variant_id) - 1 as additional_count,
                       sum(pi.qty) as total_qty,
                       min(o.receive_method) as receive_by
                from wholesale.packing pk
                join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
                join wholesale.order_item oi   on oi.id = pi.order_item_id
                join wholesale.orders o        on o.id = pk.order_id
                join wholesale.variant v       on v.id = oi.variant_id
                join wholesale.product p       on p.id = v.product_id
                where pk.outbound_id in (:ids)
                group by pk.outbound_id
                """, Map.of("ids", outboundIds), rs -> {
            result.put(rs.getLong("outbound_id"), new Summary(
                    rs.getString("first_name"), rs.getInt("additional_count"),
                    rs.getInt("total_qty"), ReceiveBy.valueOf(rs.getString("receive_by"))));
        });
        return result;
    }

    /** 상세 items — 주문을 구분하지 않고 SKU 단위로 합친다. 순서는 먼저 담긴 항목부터. */
    public List<OutboundItemResponse> skuItems(Long outboundId) {
        List<OutboundItemResponse> rows = new ArrayList<>();
        jdbc.query("""
                select oi.variant_id, p.product_number, v.variant_seq,
                       p.name as product_name, c.name as color_name, v.size, sum(pi.qty) as qty
                from wholesale.packing pk
                join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
                join wholesale.order_item oi   on oi.id = pi.order_item_id
                join wholesale.variant v       on v.id = oi.variant_id
                join wholesale.product p       on p.id = v.product_id
                join wholesale.color_option co on co.id = v.color_option_id
                join common.color c            on c.id = co.color_id
                where pk.outbound_id = :id
                group by oi.variant_id, p.product_number, v.variant_seq, p.name, c.name, v.size
                order by min(pi.id)
                """, Map.of("id", outboundId), rs -> {
            rows.add(new OutboundItemResponse(
                    rs.getLong("variant_id"), rs.getInt("product_number"), rs.getInt("variant_seq"),
                    rs.getString("product_name"), rs.getString("color_name"),
                    Size.valueOf(rs.getString("size")), rs.getInt("qty")));
        });
        return rows;
    }

    /** 주문 역추적용 포장 링크 — 합쳐진 items 만으로는 CS 가 안 된다. */
    public List<OutboundDetailResponse.PackingRef> packingRefs(Long outboundId) {
        List<OutboundDetailResponse.PackingRef> rows = new ArrayList<>();
        jdbc.query("""
                select pk.id, o.id as order_id, o.order_number
                from wholesale.packing pk
                join wholesale.orders o on o.id = pk.order_id
                where pk.outbound_id = :id
                order by pk.id
                """, Map.of("id", outboundId), rs -> {
            rows.add(new OutboundDetailResponse.PackingRef(
                    rs.getLong("id"), rs.getLong("order_id"), rs.getInt("order_number")));
        });
        return rows;
    }

    /** 헤더 집계와 봉투 목록이 같은 조건 어휘를 쓴다 — 축·필터가 어긋나면 건수가 안 맞는다. */
    private String outboundWhere(MapSqlParameterSource params, Long wholesalerId,
                                 OutboundListQuery query) {
        StringBuilder where = new StringBuilder("ob.wholesaler_id = :wholesalerId");
        params.addValue("wholesalerId", wholesalerId);
        if (query.status() != null) {
            where.append(query.status() == OutboundStatusFilter.SHIPPED
                    ? " and ob.shipped_at is not null" : " and ob.shipped_at is null");
        }
        if (query.q() != null && !query.q().isBlank()) {
            where.append(" and exists (select 1 from wholesale.packing qpk")
                    .append(" join wholesale.packing_item qpi on qpi.packing_id = qpk.id")
                    .append(" and qpi.deleted_at is null")
                    .append(" join wholesale.order_item qoi on qoi.id = qpi.order_item_id")
                    .append(" join wholesale.variant qv on qv.id = qoi.variant_id")
                    .append(" join wholesale.product qp on qp.id = qv.product_id")
                    .append(" where qpk.outbound_id = ob.id and qp.name ilike :q)");
            params.addValue("q", "%" + query.q() + "%");
        }
        String axis = (query.status() == OutboundStatusFilter.SHIPPED)
                ? "ob.shipped_at" : "ob.created_at";
        if (query.from() != null) {
            where.append(" and ").append(axis).append(" >= :from");
            params.addValue("from", KstDays.start(query.from()));
        }
        if (query.to() != null) {
            where.append(" and ").append(axis).append(" < :toNext");
            params.addValue("toNext", KstDays.startOfNext(query.to()));
        }
        return where.toString();
    }
}
