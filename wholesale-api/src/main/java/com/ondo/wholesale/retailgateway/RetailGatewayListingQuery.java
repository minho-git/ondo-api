package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.retailgateway.dto.RetailFilterOptionsResponse;
import com.ondo.wholesale.retailgateway.dto.RetailListingDetailResponse;
import com.ondo.wholesale.retailgateway.dto.RetailListingSearchCondition;
import com.ondo.wholesale.retailgateway.dto.RetailListingSummaryResponse;
import com.ondo.wholesale.retailgateway.dto.RetailVariantInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 소매 상품 조회 SQL (MUL-88).
 *
 * <p><b>엔티티를 안 만든다.</b> 읽기 전용이고 화면 모양이 그대로 SQL 이라 JPA 를 태우면
 * 엔티티 그래프를 만들었다가 다시 DTO 로 풀어야 한다. 도매 화면용 엔티티(채빈 영역)와
 * 수명도 다르다 — 그쪽이 필드를 바꿔도 여기가 안 깨지는 편이 낫다.
 *
 * <p>정렬은 전부 여기서 끝낸다. 색상은 그룹 순 → 그룹 안 순서, 사이즈는 XS~FREE 순,
 * 이미지는 sortOrder 순이다. 소매가 다시 정렬하지 않아도 되게 한다.
 */
@Repository
@RequiredArgsConstructor
public class RetailGatewayListingQuery {

    /**
     * 사이즈 정렬 축. {@code common} 에 사이즈 마스터 테이블이 없어서 SQL 안에서 순서를 준다.
     * 값과 순서는 {@code variant_size_ck} CHECK 및 {@code Size} enum 과 같아야 한다.
     */
    private static final String SIZE_ORDER =
            "array_position(ARRAY['XS','S','M','L','XL','2XL','FREE'], v.size)";

    /** 소매에 보이는 게시글의 조건. 목록·상세·필터가 전부 이걸 쓴다. */
    private static final String VISIBLE_LISTING =
            "l.status = 'ON_SALE' AND l.deleted_at IS NULL AND p.deleted_at IS NULL";

    private final JdbcClient jdbc;

    // ── 목록 ────────────────────────────────────────────────────

    /**
     * 조건에 맞는 게시글 id 를 한 장 만큼.
     *
     * <p>id 만 먼저 뽑고 카드 값은 {@link #cards(List)} 로 따로 가져온다. 한 방에 묶으면
     * 색상 필터가 색상 가짓수까지 걸러버려서, "네이비" 로 좁혔을 때 카드에 "1색" 이라고
     * 적히게 된다. 필터는 <b>어느 게시글이 나올지</b>만 정하고 카드 값은 안 건드린다.
     */
    public List<Long> searchIds(RetailListingSearchCondition condition, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(condition, params);
        params.put("limit", size);
        params.put("offset", (long) page * size);

        return jdbc.sql("""
                        SELECT l.id
                        FROM wholesale.listing l
                        JOIN wholesale.product p ON p.id = l.product_id
                        WHERE %s
                        ORDER BY l.season_started_at DESC NULLS LAST, l.id DESC
                        LIMIT :limit OFFSET :offset
                        """.formatted(where))
                .params(params)
                .query(Long.class)
                .list();
    }

    /** 조건에 맞는 전체 개수. 페이지 수를 내려면 필요하다. */
    public long countSearch(RetailListingSearchCondition condition) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(condition, params);

        return jdbc.sql("""
                        SELECT count(*)
                        FROM wholesale.listing l
                        JOIN wholesale.product p ON p.id = l.product_id
                        WHERE %s
                        """.formatted(where))
                .params(params)
                .query(Long.class)
                .single();
    }

    /**
     * WHERE 절을 조건이 있는 것만 이어 붙인다.
     *
     * <p>붙이는 건 <b>고정 문자열</b>뿐이고 값은 전부 이름 붙은 파라미터로 나간다.
     * 검색어가 SQL 로 해석될 자리가 없다.
     *
     * <p>색상·사이즈·가격은 게시글이 아니라 옵션에 걸리는 조건이라 EXISTS 안으로 들어간다.
     * JOIN 으로 풀면 같은 게시글이 옵션 수만큼 나와서 DISTINCT 로 다시 접어야 한다.
     */
    private String buildWhere(RetailListingSearchCondition condition, Map<String, Object> params) {
        List<String> clauses = new ArrayList<>();
        clauses.add(VISIBLE_LISTING);

        if (hasText(condition.q())) {
            // ILIKE 는 대소문자를 안 가린다. 한글은 영향 없고 영문 상품명에서 갈린다.
            clauses.add("l.title ILIKE '%' || :q || '%' ESCAPE '\\'");
            params.put("q", escapeLike(condition.q().trim()));
        }
        if (condition.categoryId() != null) {
            // depth 1·2 를 주면 그 아래 전부. 리프만 상품을 갖지만 필터는 상위로도 건다
            clauses.add("""
                    p.category_id IN (
                        WITH RECURSIVE sub AS (
                            SELECT id FROM common.category WHERE id = :categoryId
                            UNION ALL
                            SELECT c.id FROM common.category c JOIN sub ON c.parent_id = sub.id
                        )
                        SELECT id FROM sub
                    )""");
            params.put("categoryId", condition.categoryId());
        }

        List<String> variantClauses = new ArrayList<>();
        if (isNotEmpty(condition.colorIds())) {
            variantClauses.add("co.color_id IN (:colorIds)");
            params.put("colorIds", condition.colorIds());
        }
        if (isNotEmpty(condition.sizes())) {
            variantClauses.add("v.size IN (:sizes)");
            params.put("sizes", condition.sizes());
        }
        if (condition.priceFrom() != null) {
            variantClauses.add("lv.sale_price >= :priceFrom");
            params.put("priceFrom", condition.priceFrom());
        }
        if (condition.priceTo() != null) {
            variantClauses.add("lv.sale_price <= :priceTo");
            params.put("priceTo", condition.priceTo());
        }

        // 조건이 없어도 EXISTS 는 남긴다 — 살 수 있는 옵션이 하나도 없는 게시글은 목록에서 뺀다
        String variantWhere = variantClauses.isEmpty() ? "" : " AND " + String.join(" AND ", variantClauses);
        clauses.add("""
                EXISTS (
                    SELECT 1
                    FROM wholesale.listing_variant lv
                    JOIN wholesale.variant v       ON v.id = lv.variant_id AND v.deleted_at IS NULL
                    JOIN wholesale.color_option co ON co.id = v.color_option_id
                    WHERE lv.listing_id = l.id%s
                )""".formatted(variantWhere));

        return String.join("\n  AND ", clauses);
    }

    /** 카드 값. 필터와 무관하게 그 게시글의 전체 옵션을 센다. */
    public List<RetailListingSummaryResponse> cards(List<Long> listingIds) {
        if (listingIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT l.id                   AS listing_id,
                               l.title                AS title,
                               l.single_piece_allowed AS single_piece_allowed,
                               w.id                   AS wholesaler_id,
                               w.biz_name             AS wholesaler_name,
                               (SELECT li.url FROM wholesale.listing_image li
                                 WHERE li.listing_id = l.id
                                 ORDER BY li.sort_order, li.id LIMIT 1) AS thumbnail_url,
                               agg.min_price          AS min_price,
                               agg.color_count        AS color_count,
                               agg.size_count         AS size_count
                        FROM wholesale.listing l
                        JOIN wholesale.product p    ON p.id = l.product_id
                        JOIN wholesale.wholesaler w ON w.id = p.wholesaler_id
                        JOIN LATERAL (
                            SELECT min(lv.sale_price)          AS min_price,
                                   count(DISTINCT co.color_id) AS color_count,
                                   count(DISTINCT v.size)      AS size_count
                            FROM wholesale.listing_variant lv
                            JOIN wholesale.variant v       ON v.id = lv.variant_id AND v.deleted_at IS NULL
                            JOIN wholesale.color_option co ON co.id = v.color_option_id
                            WHERE lv.listing_id = l.id
                        ) agg ON true
                        WHERE l.id IN (:ids)
                        """)
                .param("ids", listingIds)
                .query((rs, rowNum) -> new RetailListingSummaryResponse(
                        rs.getLong("listing_id"),
                        rs.getString("title"),
                        new RetailListingSummaryResponse.Wholesaler(
                                rs.getLong("wholesaler_id"), rs.getString("wholesaler_name")),
                        rs.getString("thumbnail_url"),
                        (Integer) rs.getObject("min_price"),
                        rs.getInt("color_count"),
                        rs.getInt("size_count"),
                        rs.getBoolean("single_piece_allowed")))
                .list();
    }

    // ── 상세 ────────────────────────────────────────────────────

    /**
     * 상세의 뼈대. 게시 중이 아니면 비어 있다.
     *
     * <p>없는 것 · 시즌이 끝난 것 · 지워진 것을 구분하지 않는다. 도매가 시즌을 닫은 건지
     * 원래 없는 건지 소매가 알 이유가 없다.
     */
    public Optional<ListingHeader> findHeader(long listingId) {
        return jdbc.sql("""
                        SELECT l.id                   AS listing_id,
                               l.title                AS title,
                               l.description          AS description,
                               l.single_piece_allowed AS single_piece_allowed,
                               p.product_number       AS product_number,
                               p.category_id          AS category_id,
                               w.id                   AS wholesaler_id,
                               w.biz_name             AS wholesaler_name,
                               w.store_building       AS store_building,
                               w.store_unit           AS store_unit,
                               agg.min_price          AS min_price,
                               agg.max_price          AS max_price,
                               agg.listed_count       AS listed_count,
                               (SELECT count(*) FROM wholesale.variant v2
                                 WHERE v2.product_id = p.id AND v2.deleted_at IS NULL) AS total_count
                        FROM wholesale.listing l
                        JOIN wholesale.product p    ON p.id = l.product_id
                        JOIN wholesale.wholesaler w ON w.id = p.wholesaler_id
                        JOIN LATERAL (
                            SELECT min(lv.sale_price) AS min_price,
                                   max(lv.sale_price) AS max_price,
                                   count(*)           AS listed_count
                            FROM wholesale.listing_variant lv
                            JOIN wholesale.variant v ON v.id = lv.variant_id AND v.deleted_at IS NULL
                            WHERE lv.listing_id = l.id
                        ) agg ON true
                        WHERE l.id = :listingId AND %s
                        """.formatted(VISIBLE_LISTING))
                .param("listingId", listingId)
                .query((rs, rowNum) -> new ListingHeader(
                        rs.getLong("listing_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("product_number"),
                        rs.getBoolean("single_piece_allowed"),
                        (Integer) rs.getObject("min_price"),
                        (Integer) rs.getObject("max_price"),
                        rs.getInt("listed_count"),
                        rs.getInt("total_count"),
                        rs.getLong("category_id"),
                        new RetailListingDetailResponse.Wholesaler(
                                rs.getLong("wholesaler_id"),
                                rs.getString("wholesaler_name"),
                                rs.getString("store_building"),
                                rs.getString("store_unit"))))
                .optional();
    }

    /** 게시된 옵션을 색상 순 → 사이즈 순으로. 서비스가 색상별로 접는다. */
    public List<ColorVariantRow> colorVariants(long listingId) {
        return jdbc.sql("""
                        SELECT c.id        AS color_id,
                               c.name      AS color_name,
                               c.hex       AS hex,
                               cg.name     AS group_name,
                               v.id        AS variant_id,
                               v.size      AS size,
                               lv.sale_price  AS sale_price,
                               lv.order_limit AS order_limit
                        FROM wholesale.listing_variant lv
                        JOIN wholesale.variant v       ON v.id = lv.variant_id AND v.deleted_at IS NULL
                        JOIN wholesale.color_option co ON co.id = v.color_option_id
                        JOIN common.color c            ON c.id = co.color_id
                        JOIN common.color_group cg     ON cg.id = c.group_id
                        WHERE lv.listing_id = :listingId
                        ORDER BY cg.sort_order, cg.id, c.sort_order, c.id, %s
                        """.formatted(SIZE_ORDER))
                .param("listingId", listingId)
                .query((rs, rowNum) -> new ColorVariantRow(
                        rs.getLong("color_id"),
                        rs.getString("color_name"),
                        rs.getString("hex"),
                        rs.getString("group_name"),
                        rs.getLong("variant_id"),
                        rs.getString("size"),
                        rs.getInt("sale_price"),
                        rs.getInt("order_limit")))
                .list();
    }

    public List<RetailListingDetailResponse.Image> images(long listingId) {
        return jdbc.sql("""
                        SELECT id, url, sort_order
                        FROM wholesale.listing_image
                        WHERE listing_id = :listingId
                        ORDER BY sort_order, id
                        """)
                .param("listingId", listingId)
                .query((rs, rowNum) -> new RetailListingDetailResponse.Image(
                        rs.getLong("id"), rs.getString("url"), rs.getInt("sort_order")))
                .list();
    }

    // ── 마스터 ──────────────────────────────────────────────────

    /** 게시 중인 상품의 실제 최저·최고가. 하나도 없으면 둘 다 null 이다. */
    public RetailFilterOptionsResponse.PriceRange priceRange() {
        return jdbc.sql("""
                        SELECT min(lv.sale_price) AS min_price, max(lv.sale_price) AS max_price
                        FROM wholesale.listing_variant lv
                        JOIN wholesale.listing l ON l.id = lv.listing_id
                        JOIN wholesale.product p ON p.id = l.product_id
                        JOIN wholesale.variant v ON v.id = lv.variant_id AND v.deleted_at IS NULL
                        WHERE %s
                        """.formatted(VISIBLE_LISTING))
                .query((rs, rowNum) -> new RetailFilterOptionsResponse.PriceRange(
                        (Integer) rs.getObject("min_price"), (Integer) rs.getObject("max_price")))
                .single();
    }

    // ── 옵션 배치 (장바구니) ────────────────────────────────────

    /**
     * 옵션 여러 개를 한 번에. 소매 장바구니가 담긴 개수만큼 부르지 않게 묶어서 받는다.
     *
     * <p>게시가 내려간 것도 돌려준다 — 소매 장바구니 행이 남아 있어서다. 대신
     * {@code orderable} 이 false 로 간다. 게시 자체가 없는 옵션은 행이 안 나온다.
     */
    public List<RetailVariantInfoResponse> variants(List<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT v.id       AS variant_id,
                               l.id       AS listing_id,
                               l.title    AS title,
                               (SELECT li.url FROM wholesale.listing_image li
                                 WHERE li.listing_id = l.id
                                 ORDER BY li.sort_order, li.id LIMIT 1) AS thumbnail_url,
                               c.name     AS color_name,
                               v.size     AS size,
                               lv.sale_price  AS sale_price,
                               lv.order_limit AS order_limit,
                               w.id       AS wholesaler_id,
                               w.biz_name AS wholesaler_name,
                               (l.status = 'ON_SALE'
                                    AND l.deleted_at IS NULL
                                    AND p.deleted_at IS NULL
                                    AND v.deleted_at IS NULL) AS orderable
                        FROM wholesale.variant v
                        JOIN wholesale.color_option co    ON co.id = v.color_option_id
                        JOIN common.color c               ON c.id = co.color_id
                        JOIN wholesale.product p          ON p.id = v.product_id
                        JOIN wholesale.wholesaler w       ON w.id = p.wholesaler_id
                        JOIN wholesale.listing l          ON l.product_id = p.id
                        JOIN wholesale.listing_variant lv ON lv.listing_id = l.id AND lv.variant_id = v.id
                        WHERE v.id IN (:ids)
                        """)
                .param("ids", variantIds)
                .query((rs, rowNum) -> new RetailVariantInfoResponse(
                        rs.getLong("variant_id"),
                        rs.getLong("listing_id"),
                        rs.getString("title"),
                        rs.getString("thumbnail_url"),
                        rs.getString("color_name"),
                        rs.getString("size"),
                        rs.getInt("sale_price"),
                        rs.getInt("order_limit"),
                        rs.getLong("wholesaler_id"),
                        rs.getString("wholesaler_name"),
                        rs.getBoolean("orderable")))
                .list();
    }

    // ── 조립 전 중간 모양 ───────────────────────────────────────

    /** 상세의 뼈대. 카테고리 경로·색상·이미지는 따로 붙인다. */
    public record ListingHeader(
            Long listingId,
            String title,
            String description,
            Integer productNumber,
            Boolean isSinglePieceAllowed,
            Integer minSalePrice,
            Integer maxSalePrice,
            Integer listedVariantCount,
            Integer totalVariantCount,
            Long categoryId,
            RetailListingDetailResponse.Wholesaler wholesaler) {}

    /** 색상 × 사이즈 한 줄. 같은 색이 사이즈 수만큼 반복해서 나온다. */
    public record ColorVariantRow(
            Long colorId, String colorName, String hex, String groupName,
            Long variantId, String size, Integer salePrice, Integer orderLimit) {}

    /**
     * LIKE 의 문법 글자를 막는다.
     *
     * <p>{@code %} 와 {@code _} 는 LIKE 에서 "아무 글자" 를 뜻한다. 그대로 넣으면
     * {@code q=%} 하나로 전건이 나온다 — 사용자는 그 글자를 <b>찾아달라</b>고 친 것이다.
     */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
