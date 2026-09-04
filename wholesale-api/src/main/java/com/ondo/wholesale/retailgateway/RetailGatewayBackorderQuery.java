package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.retailgateway.dto.RetailBackorderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 소매 미송 조회 SQL (MUL-97).
 *
 * <p>{@link RetailGatewayListingQuery} 와 같은 이유로 엔티티를 안 만든다 — 읽기 전용이고
 * 화면 모양이 그대로 SQL 이다. 미송·주문 엔티티는 채빈 영역(MUL-47·MUL-48)이라
 * 그쪽이 필드를 바꿔도 여기가 안 깨지는 편이 낫다.
 */
@Repository
@RequiredArgsConstructor
public class RetailGatewayBackorderQuery {

    /**
     * 미송 한 줄이 딛고 서는 조인.
     *
     * <p>게시글만 {@code LEFT JOIN} 이다. 도매가 상품을 지우면 게시글도 같이 지워지는데
     * (V1 의 {@code product.deleted_at} 주석), 그렇다고 이미 주문한 미송이 목록에서
     * 사라지면 안 된다. 소매처는 여전히 그 물건을 기다리는 중이다.
     */
    private static final String FROM = """
            FROM wholesale.backorder b
            JOIN wholesale.order_item oi   ON oi.id = b.order_item_id
            JOIN wholesale.orders o        ON o.id = oi.order_id
            JOIN wholesale.partner pt      ON pt.id = o.partner_id
            JOIN wholesale.wholesaler w    ON w.id = o.wholesaler_id
            JOIN wholesale.variant v       ON v.id = oi.variant_id
            JOIN wholesale.color_option co ON co.id = v.color_option_id
            JOIN common.color c            ON c.id = co.color_id
            JOIN wholesale.product p       ON p.id = v.product_id
            LEFT JOIN wholesale.listing l  ON l.product_id = p.id AND l.deleted_at IS NULL
            """;

    /**
     * 소매에 보이는 미송의 조건. 목록과 개수가 같은 걸 써야 페이지 수가 안 어긋난다.
     *
     * <p>{@code retail_order_id IS NOT NULL} 이 있는 건 도매가 자기 화면에서 직접 넣은 주문
     * 때문이다. 그건 소매 앱을 안 거쳐서 소매 DB 에 주문서가 없다 — 내려봐야 소매가
     * 주문번호를 못 채운다.
     */
    private static final String VISIBLE_BACKORDER = """
            b.status = 'OPEN'
              AND pt.retailer_id = :retailerId
              AND o.retail_order_id IS NOT NULL
            """;

    private final JdbcClient jdbc;

    /**
     * 미송 대기 한 장. <b>오래된 순</b>이다 — 미송은 FIFO 로 풀린다.
     *
     * <p>정렬 축이 미송 발생({@code b.created_at})이 아니라 주문 시각({@code o.ordered_at})인 건
     * 소매 계약이 그렇게 적혀 있어서다. 도매 화면은 발생 시각으로 센다(경과일). 접수 한 번에
     * 미송이 여러 줄 생기므로 둘은 대개 같은 순서지만, 같은 주문 안에서 갈릴 때를 대비해
     * {@code b.id} 로 한 번 더 묶는다.
     */
    public List<RetailBackorderResponse> search(long retailerId, int page, int size) {
        return jdbc.sql("""
                        SELECT b.id                      AS backorder_id,
                               b.qty                     AS qty,
                               o.retail_order_id         AS retail_order_id,
                               o.ordered_at              AS ordered_at,
                               w.id                      AS wholesaler_id,
                               w.biz_name                AS wholesaler_name,
                               l.id                      AS listing_id,
                               COALESCE(l.title, p.name) AS title,
                               c.name                    AS color_name,
                               v.size                    AS size,
                               v.expected_inbound_date   AS expected_inbound_date,
                               v.expected_inbound_reason AS expected_inbound_reason
                        %s
                        WHERE %s
                        ORDER BY o.ordered_at, b.id
                        LIMIT :limit OFFSET :offset
                        """.formatted(FROM, VISIBLE_BACKORDER))
                .params(Map.of("retailerId", retailerId,
                               "limit", size,
                               "offset", (long) page * size))
                .query((rs, rowNum) -> new RetailBackorderResponse(
                        rs.getLong("backorder_id"),
                        rs.getLong("retail_order_id"),
                        rs.getObject("ordered_at", OffsetDateTime.class),
                        new RetailBackorderResponse.Wholesaler(
                                rs.getLong("wholesaler_id"), rs.getString("wholesaler_name")),
                        (Long) rs.getObject("listing_id"),
                        rs.getString("title"),
                        rs.getString("color_name"),
                        rs.getString("size"),
                        rs.getInt("qty"),
                        rs.getObject("expected_inbound_date", LocalDate.class),
                        rs.getString("expected_inbound_reason")))
                .list();
    }

    /** 전체 개수. 페이지 수를 내려면 필요하다. */
    public long count(long retailerId) {
        return jdbc.sql("""
                        SELECT count(*)
                        %s
                        WHERE %s
                        """.formatted(FROM, VISIBLE_BACKORDER))
                .param("retailerId", retailerId)
                .query(Long.class)
                .single();
    }
}
