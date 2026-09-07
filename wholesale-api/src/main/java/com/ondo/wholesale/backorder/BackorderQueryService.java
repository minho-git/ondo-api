package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.BackorderListResponse;
import com.ondo.wholesale.backorder.dto.BackorderResponse;
import com.ondo.wholesale.backorder.dto.BackorderSkuResponse;
import com.ondo.wholesale.backorder.dto.BackorderStatsResponse;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ResourceNotFoundException;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.product.domain.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 미송 조회 2종 (MUL-48) — SKU 목록(아코디언 헤더)과 SKU별 미송(펼침).
 *
 * <p>미송 엔티티를 조립하지 않고 SQL 로 바로 읽는다 — 화면 모양이 곧 집계 쿼리라서다
 * (retailgateway 의 선례). 잔여는 라인의 {@code qty − allocated_qty} 파생이라 저장값이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackorderQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    /** OPEN 미송 한 줄이 딛고 서는 조인 — 목록·펼침이 같은 바닥을 쓴다. */
    private static final String OPEN_BACKORDER_FROM = """
            from wholesale.backorder b
            join wholesale.order_item oi on oi.id = b.order_item_id
            join wholesale.orders o      on o.id = oi.order_id
            """;

    private final JdbcClient jdbc;

    /** 정렬 화이트리스트 — 요청 키를 ORDER BY 컬럼으로만 바꾼다(문자열 조립 방어). */
    private static final Map<String, String> SKU_SORT_COLUMNS = Map.of(
            "latestBackorderedAt", "latest_backordered_at",
            "backorderQty", "backorder_qty");

    /** 미송 SKU 목록 — OPEN 미송만 SKU 단위 집계, 전부 해소된 SKU 는 자동 제외. */
    public ApiResponse<List<BackorderSkuResponse>> skuList(Long wholesalerId, String q,
                                                           int page, int size, String sort) {
        if (size > MAX_PAGE_SIZE) {
            throw ApiException.validationFailed("size", "size 는 최대 " + MAX_PAGE_SIZE + " 이다.");
        }
        String orderBy = parseSkuSort(sort);
        boolean searching = (q != null && !q.isBlank());
        String where = """
                where b.status = 'OPEN' and o.wholesaler_id = :wholesalerId
                """ + (searching ? " and p.name like '%' || :q || '%'" : "");
        String joins = OPEN_BACKORDER_FROM + """
                join wholesale.variant v on v.id = oi.variant_id
                join wholesale.product p on p.id = v.product_id
                """;

        var countSpec = jdbc.sql("select count(distinct v.id) " + joins + where)
                .param("wholesalerId", wholesalerId);
        if (searching) {
            countSpec = countSpec.param("q", q);
        }
        long total = countSpec.query(Long.class).single();

        var rowSpec = jdbc.sql("""
                        select v.id as variant_id, v.product_id, p.product_number, v.variant_seq,
                               p.name as product_name, c.name as color_name, v.size,
                               sum(oi.qty - oi.allocated_qty) as backorder_qty,
                               max(b.created_at) as latest_backordered_at,
                               v.stock_qty - v.reserved_qty as available_qty,
                               v.expected_inbound_date
                        """ + joins + """
                        join wholesale.color_option co on co.id = v.color_option_id
                        join common.color c            on c.id = co.color_id
                        """ + where + """
                        group by v.id, v.product_id, p.product_number, v.variant_seq,
                                 p.name, c.name, v.size, v.stock_qty, v.reserved_qty,
                                 v.expected_inbound_date
                        order by %s, v.id desc
                        limit :limit offset :offset
                        """.formatted(orderBy))
                .param("wholesalerId", wholesalerId)
                .param("limit", size)
                .param("offset", (long) page * size);
        if (searching) {
            rowSpec = rowSpec.param("q", q);
        }
        List<BackorderSkuResponse> rows = rowSpec.query((rs, rowNum) -> new BackorderSkuResponse(
                        rs.getLong("variant_id"), rs.getLong("product_id"),
                        rs.getInt("product_number"), rs.getInt("variant_seq"),
                        rs.getString("product_name"), rs.getString("color_name"),
                        Size.fromLabel(rs.getString("size")),
                        rs.getInt("backorder_qty"), rs.getInt("available_qty"),
                        rs.getObject("expected_inbound_date", LocalDate.class)))
                .list();

        int totalPages = (int) ((total + size - 1) / size);
        return ApiResponse.paged(rows, new ApiResponse.PageMeta(page, size, total, totalPages));
    }

    /** SKU별 미송(FIFO) + 요약 — 좌측 표와 우측 패널을 같은 트랜잭션에서 한 번에 채운다. */
    public BackorderListResponse backordersOfSku(Long wholesalerId, Long variantId, String sort) {
        boolean latestFirst = parseFifoSort(sort);
        VariantHead head = jdbc.sql("""
                        select v.id, p.product_number, v.variant_seq,
                               v.stock_qty - v.reserved_qty as available_qty,
                               v.expected_inbound_date, v.expected_inbound_reason
                        from wholesale.variant v
                        join wholesale.product p on p.id = v.product_id
                        where v.id = :variantId and p.wholesaler_id = :wholesalerId
                          and v.deleted_at is null
                        """)
                .param("variantId", variantId)
                .param("wholesalerId", wholesalerId)
                .query((rs, rowNum) -> new VariantHead(
                        rs.getInt("product_number"), rs.getInt("variant_seq"),
                        rs.getInt("available_qty"),
                        rs.getObject("expected_inbound_date", LocalDate.class),
                        rs.getString("expected_inbound_reason")))
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("SKU 가 없거나 접근할 수 없습니다."));

        List<BackorderResponse> rows = jdbc.sql("""
                        select b.id, o.id as order_id, o.order_number, oi.id as order_item_id,
                               o.ordered_at, b.created_at,
                               extract(day from now() - b.created_at)::int as elapsed_days,
                               pt.retailer_id, pt.retailer_name, b.qty,
                               oi.qty - oi.allocated_qty as remaining_qty, oi.unit_price
                        """ + OPEN_BACKORDER_FROM + """
                        join wholesale.partner pt on pt.id = o.partner_id
                        where oi.variant_id = :variantId and b.status = 'OPEN'
                        order by b.created_at %s, b.id %s
                        """.formatted(latestFirst ? "desc" : "asc", latestFirst ? "desc" : "asc"))
                .param("variantId", variantId)
                .query((rs, rowNum) -> new BackorderResponse(
                        rs.getLong("id"), rs.getLong("order_id"), rs.getInt("order_number"),
                        rs.getLong("order_item_id"),
                        rs.getObject("ordered_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getInt("elapsed_days"),
                        rs.getLong("retailer_id"), rs.getString("retailer_name"),
                        rs.getInt("qty"), rs.getInt("remaining_qty"), rs.getInt("unit_price")))
                .list();

        return new BackorderListResponse(rows, stats(variantId, head, rows));
    }

    /** 요약은 표와 같은 행에서 계산한다 — 두 쿼리가 다른 순간을 보지 않게. */
    private BackorderStatsResponse stats(Long variantId, VariantHead head, List<BackorderResponse> rows) {
        int backorderQty = rows.stream().mapToInt(BackorderResponse::remainingQty).sum();
        int orderCount = (int) rows.stream().map(BackorderResponse::orderId).distinct().count();
        int retailerCount = (int) rows.stream().map(BackorderResponse::retailerId).distinct().count();
        int backorderAmount = rows.stream()
                .mapToInt(row -> row.remainingQty() * row.unitPrice()).sum();
        Optional<OffsetDateTime> firstOrderedAt = rows.stream()
                .map(BackorderResponse::orderedAt).min(Comparator.naturalOrder());
        Optional<OffsetDateTime> lastOrderedAt = rows.stream()
                .map(BackorderResponse::orderedAt).max(Comparator.naturalOrder());
        return new BackorderStatsResponse(
                variantId, head.productNumber(), head.variantNumber(),
                backorderQty, orderCount, retailerCount, head.availableQty(),
                head.expectedInboundDate(), head.expectedInboundReason(),
                firstOrderedAt.orElse(null), lastOrderedAt.orElse(null), backorderAmount);
    }

    /**
     * 정렬 파싱 — 기본은 가장 최근에 미송이 쌓인 순({@code latestBackorderedAt,desc} —
     * SKU 별 OPEN 미송의 {@code max(created_at)}). 키는 {@code latestBackorderedAt}·
     * {@code backorderQty} 둘뿐이고 방향 없는 키는 SortParser 관례대로 오름차순이다.
     */
    private String parseSkuSort(String raw) {
        String value = (raw == null || raw.isBlank()) ? "latestBackorderedAt,desc" : raw;
        String[] parts = value.split(",", 2);
        String column = SKU_SORT_COLUMNS.get(parts[0].trim());
        String direction = (parts.length < 2) ? "asc"
                : "desc".equalsIgnoreCase(parts[1].trim()) ? "desc"
                : "asc".equalsIgnoreCase(parts[1].trim()) ? "asc"
                : null;
        if (column == null || direction == null) {
            throw ApiException.validationFailed("sort", "지원하지 않는 정렬: " + raw);
        }
        return column + " " + direction;
    }

    /** 기본은 FIFO(createdAt 오름차순 — 제안일 뿐 강제가 아니다). "createdAt,desc"만 추가 허용. */
    private boolean parseFifoSort(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("createdAt") || raw.equals("createdAt,asc")) {
            return false;
        }
        if (raw.equals("createdAt,desc")) {
            return true;
        }
        throw ApiException.validationFailed("sort", "지원하지 않는 정렬: " + raw);
    }

    private record VariantHead(int productNumber, int variantNumber, int availableQty,
                               LocalDate expectedInboundDate, String expectedInboundReason) {
    }
}
