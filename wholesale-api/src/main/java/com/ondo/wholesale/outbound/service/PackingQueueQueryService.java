package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.outbound.dto.PackingItemRowResponse;
import com.ondo.wholesale.outbound.dto.PackingRetailerResponse;
import com.ondo.wholesale.product.domain.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 포장 대기열 조회 (MUL-49). 페이징 없음 — 지금 대기 중인 것만이라 수가 제한적이다.
 *
 * <p>"대기열"은 실체가 없다 (D-073) — {@code packing.status = READY AND outbound_id IS NULL}
 * 이고 살아있는({@code deleted_at IS NULL}) 항목이 조건의 전부다. 집계는 현재 필터
 * (q·receiveBy)를 반영하므로 헤더와 펼침이 같은 조건을 공유한다. q 는 상품명만 거른다 —
 * 소매처명은 외부 시스템 값이라 검색 대상이 아니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PackingQueueQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public List<PackingRetailerResponse> retailers(Long wholesalerId, String q, ReceiveBy receiveBy) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = queueWhere(params, wholesalerId, null, q, receiveBy);
        List<PackingRetailerResponse> rows = new ArrayList<>();
        jdbc.query("""
                select pt.retailer_id, pt.retailer_name,
                       count(*) as item_count, sum(pi.qty) as total_qty
                from wholesale.packing_item pi
                join wholesale.packing pk on pk.id = pi.packing_id
                join wholesale.orders o   on o.id = pk.order_id
                join wholesale.partner pt on pt.id = o.partner_id
                where %s
                group by pt.retailer_id, pt.retailer_name
                order by pt.retailer_name, pt.retailer_id
                """.formatted(where), params, rs -> {
            rows.add(new PackingRetailerResponse(
                    rs.getLong("retailer_id"), rs.getString("retailer_name"),
                    rs.getInt("item_count"), rs.getInt("total_qty")));
        });
        return rows;
    }

    public List<PackingItemRowResponse> items(Long wholesalerId, Long retailerId,
                                              String q, ReceiveBy receiveBy) {
        requireTradedRetailer(wholesalerId, retailerId);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = queueWhere(params, wholesalerId, retailerId, q, receiveBy);
        List<PackingItemRowResponse> rows = new ArrayList<>();
        jdbc.query("""
                select pi.id, pi.packing_id, pi.qty, o.id as order_id, o.order_number,
                       o.receive_method, o.ordered_at, oi.variant_id, v.variant_seq, v.size,
                       p.product_number, p.name as product_name, c.name as color_name
                from wholesale.packing_item pi
                join wholesale.packing pk      on pk.id = pi.packing_id
                join wholesale.orders o        on o.id = pk.order_id
                join wholesale.partner pt      on pt.id = o.partner_id
                join wholesale.order_item oi   on oi.id = pi.order_item_id
                join wholesale.variant v       on v.id = oi.variant_id
                join wholesale.product p       on p.id = v.product_id
                join wholesale.color_option co on co.id = v.color_option_id
                join common.color c            on c.id = co.color_id
                where %s
                order by o.ordered_at, pi.id
                """.formatted(where), params, rs -> {
            rows.add(new PackingItemRowResponse(
                    rs.getLong("id"), rs.getLong("packing_id"),
                    rs.getLong("order_id"), rs.getInt("order_number"),
                    rs.getLong("variant_id"), rs.getInt("product_number"), rs.getInt("variant_seq"),
                    rs.getString("product_name"), rs.getString("color_name"),
                    // DB 는 '2XL' 라벨로 저장한다 (SizeConverter) — valueOf 는 상수명이라 깨진다
                    Size.fromLabel(rs.getString("size")),
                    ReceiveBy.valueOf(rs.getString("receive_method")),
                    rs.getObject("ordered_at", OffsetDateTime.class),
                    rs.getInt("qty")));
        });
        return rows;
    }

    /** 헤더·펼침 공용 대기열 조건 — 같은 필터라야 헤더의 집계와 펼침 행 수가 맞는다. */
    private String queueWhere(MapSqlParameterSource params, Long wholesalerId,
                              Long retailerId, String q, ReceiveBy receiveBy) {
        StringBuilder where = new StringBuilder("""
                o.wholesaler_id = :wholesalerId
                  and pk.status = 'READY' and pk.outbound_id is null
                  and pi.deleted_at is null""");
        params.addValue("wholesalerId", wholesalerId);
        if (retailerId != null) {
            where.append(" and pt.retailer_id = :retailerId");
            params.addValue("retailerId", retailerId);
        }
        if (receiveBy != null) {
            where.append(" and o.receive_method = :receiveBy");
            params.addValue("receiveBy", receiveBy.name());
        }
        if (q != null && !q.isBlank()) {
            where.append(" and exists (select 1 from wholesale.order_item qoi")
                    .append(" join wholesale.variant qv on qv.id = qoi.variant_id")
                    .append(" join wholesale.product qp on qp.id = qv.product_id")
                    .append(" where qoi.id = pi.order_item_id and qp.name ilike :q)");
            params.addValue("q", "%" + q + "%");
        }
        return where.toString();
    }

    /** 거래 이력 없는 retailerId 는 404 — 필터에 안 걸린 200 빈 배열과 구분한다. */
    private void requireTradedRetailer(Long wholesalerId, Long retailerId) {
        if (retailerId == null) {
            return;
        }
        Integer traded = jdbc.queryForObject("""
                select count(*) from wholesale.partner
                where wholesaler_id = :wholesalerId and retailer_id = :retailerId
                """, new MapSqlParameterSource()
                        .addValue("wholesalerId", wholesalerId)
                        .addValue("retailerId", retailerId),
                Integer.class);
        if (traded == null || traded == 0) {
            throw new ResourceNotFoundException("거래 이력이 없는 소매처입니다.");
        }
    }
}
