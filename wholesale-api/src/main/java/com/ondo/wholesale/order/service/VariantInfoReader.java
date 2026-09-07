package com.ondo.wholesale.order.service;

import com.ondo.wholesale.product.domain.Size;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 라인 표시에 필요한 변형 스냅샷 배치 조회 (MUL-47) — 상세·포장 카드 조립이 공유한다.
 */
@Component
public class VariantInfoReader {

    /** 품번·상품명·색상·사이즈와 SKU 가용재고(재고 − 예약). */
    public record VariantInfo(int productNumber, int variantNumber, String productName,
                              String colorName, Size size, int availableQty) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public VariantInfoReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, VariantInfo> read(List<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, VariantInfo> result = new HashMap<>();
        jdbc.query("""
                select v.id, v.variant_seq, v.size, v.stock_qty - v.reserved_qty as available,
                       p.product_number, p.name as product_name, c.name as color_name
                from wholesale.variant v
                join wholesale.product p       on p.id = v.product_id
                join wholesale.color_option co on co.id = v.color_option_id
                join common.color c            on c.id = co.color_id
                where v.id in (:ids)
                """, Map.of("ids", variantIds), rs -> {
            result.put(rs.getLong("id"), new VariantInfo(
                    rs.getInt("product_number"), rs.getInt("variant_seq"),
                    rs.getString("product_name"), rs.getString("color_name"),
                    // DB 는 '2XL' 라벨로 저장한다 (SizeConverter) — valueOf 는 상수명이라 깨진다
                    Size.fromLabel(rs.getString("size")), rs.getInt("available")));
        });
        return result;
    }
}
