package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소매 상품 조회 API (MUL-88).
 *
 * <p><b>개발자 로컬 시드에 기대지 않는다.</b> 데이터를 이 안에서 직접 넣는다 —
 * {@code docs-local/} 의 시드는 gitignore 라 CI 에도 남의 컴퓨터에도 없고, 있더라도
 * 손으로 고칠 수 있는 물건이라 테스트 결과가 거기에 좌우되면 안 된다.
 *
 * <p>{@code @Transactional} 이라 넣은 데이터는 테스트가 끝나면 롤백된다.
 * 컨테이너는 JVM 당 하나를 같이 쓰므로 남기면 다른 테스트가 흔들린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RetailGatewayListingApiTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void 데이터를_넣는다() {
        // 카테고리 3단 — 여성 > 의류 > 상의
        jdbc.update("INSERT INTO common.category (id, parent_id, name, depth, sort_order) VALUES (901, NULL, '여성', 1, 1)");
        jdbc.update("INSERT INTO common.category (id, parent_id, name, depth, sort_order) VALUES (902, 901, '의류', 2, 1)");
        jdbc.update("INSERT INTO common.category (id, parent_id, name, depth, sort_order) VALUES (903, 902, '상의', 3, 1)");

        // 색상 — 그룹 순서가 상세의 색상 정렬 축이다
        jdbc.update("INSERT INTO common.color_group (id, name, sort_order) VALUES (901, '무채색', 1)");
        jdbc.update("INSERT INTO common.color_group (id, name, sort_order) VALUES (902, '레드', 2)");
        jdbc.update("INSERT INTO common.color (id, group_id, name, hex, sort_order) VALUES (901, 901, '블랙', '#111111', 1)");
        jdbc.update("INSERT INTO common.color (id, group_id, name, hex, sort_order) VALUES (902, 902, '체리레드', '#C0392B', 1)");

        jdbc.update("""
                INSERT INTO wholesale.wholesaler
                    (id, email, password_hash, biz_reg_no, biz_name, biz_owner_name,
                     store_building, store_unit, approval_status, last_product_seq)
                VALUES (901, 'gw-test@ondo.test', 'x', '9990000001', '테스트도매', '테스트',
                        '청평화패션몰', '2층 24호', 'APPROVED', 2)
                """);

        // 상품 1 — 게시 중. 색 2개 × 사이즈 3개
        jdbc.update("INSERT INTO wholesale.product (id, wholesaler_id, product_number, name, category_id, last_variant_seq)"
                + " VALUES (901, 901, 1, '빈티지 셔츠', 903, 3)");
        jdbc.update("INSERT INTO wholesale.color_option (id, product_id, color_id) VALUES (901, 901, 902)");
        jdbc.update("INSERT INTO wholesale.color_option (id, product_id, color_id) VALUES (902, 901, 901)");
        jdbc.update("INSERT INTO wholesale.variant (id, color_option_id, product_id, size, variant_seq) VALUES (901, 901, 901, 'S', 1)");
        jdbc.update("INSERT INTO wholesale.variant (id, color_option_id, product_id, size, variant_seq) VALUES (902, 901, 901, 'M', 2)");
        jdbc.update("INSERT INTO wholesale.variant (id, color_option_id, product_id, size, variant_seq) VALUES (903, 902, 901, 'L', 3)");
        jdbc.update("INSERT INTO wholesale.listing (id, product_id, title, description, single_piece_allowed, status, season_started_at)"
                + " VALUES (901, 901, '빈티지 플라워 셔츠', '봄 신상', false, 'ON_SALE', now())");
        jdbc.update("INSERT INTO wholesale.listing_variant (listing_id, variant_id, sale_price, order_limit) VALUES (901, 901, 12500, 500)");
        jdbc.update("INSERT INTO wholesale.listing_variant (listing_id, variant_id, sale_price, order_limit) VALUES (901, 902, 12500, 500)");
        jdbc.update("INSERT INTO wholesale.listing_variant (listing_id, variant_id, sale_price, order_limit) VALUES (901, 903, 13500, 0)");
        jdbc.update("INSERT INTO wholesale.listing_image (id, listing_id, url, sort_order) VALUES (901, 901, 'cover.jpg', 0)");
        jdbc.update("INSERT INTO wholesale.listing_image (id, listing_id, url, sort_order) VALUES (902, 901, 'sub.jpg', 1)");

        // 상품 2 — 시즌 종료. 목록·상세에서 빠지되 옵션 조회로는 잡혀야 한다
        jdbc.update("INSERT INTO wholesale.product (id, wholesaler_id, product_number, name, category_id, last_variant_seq)"
                + " VALUES (902, 901, 2, '지난 원피스', 903, 1)");
        jdbc.update("INSERT INTO wholesale.color_option (id, product_id, color_id) VALUES (903, 902, 901)");
        jdbc.update("INSERT INTO wholesale.variant (id, color_option_id, product_id, size, variant_seq) VALUES (904, 903, 902, 'M', 1)");
        jdbc.update("INSERT INTO wholesale.listing (id, product_id, title, single_piece_allowed, status, season_started_at, season_ended_at)"
                + " VALUES (902, 902, '시즌 종료 원피스', false, 'SEASON_ENDED', now() - interval '90 day', now())");
        jdbc.update("INSERT INTO wholesale.listing_variant (listing_id, variant_id, sale_price, order_limit) VALUES (902, 904, 19000, 0)");
    }

    @Test
    @DisplayName("목록은 게시 중인 것만 내려주고 카드 값은 전체 옵션 기준이다")
    void 목록은_게시중인것만_내려준다() throws Exception {
        mvc.perform(get("/api/retail-gateway/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.listingId == 901)]").exists())
                // 시즌이 끝난 902 는 나오면 안 된다
                .andExpect(jsonPath("$.data[?(@.listingId == 902)]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.listingId == 901)].minSalePrice").value(12500))
                .andExpect(jsonPath("$.data[?(@.listingId == 901)].colorCount").value(2))
                .andExpect(jsonPath("$.data[?(@.listingId == 901)].sizeCount").value(3))
                .andExpect(jsonPath("$.data[?(@.listingId == 901)].thumbnailUrl").value("cover.jpg"))
                .andExpect(jsonPath("$.meta.page").value(0));
    }

    @Test
    @DisplayName("한글 검색어가 실제로 거른다 — 목일 때는 q 를 통째로 무시했다")
    void 한글_검색어가_실제로_거른다() throws Exception {
        mvc.perform(get("/api/retail-gateway/listings").param("q", "빈티지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingId").value(901));

        mvc.perform(get("/api/retail-gateway/listings").param("q", "없는말"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("검색어에 SQL 이 섞여도 조건으로 해석되지 않는다")
    void 검색어의_SQL은_값으로만_다뤄진다() throws Exception {
        // 조건으로 해석됐다면 전건이 나온다. 값으로 다뤄지면 그런 제목이 없어 0건이다
        mvc.perform(get("/api/retail-gateway/listings").param("q", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(get("/api/retail-gateway/listings").param("q", "100% | drop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("검색어의 % · _ 는 와일드카드가 아니라 찾을 글자다")
    void 검색어의_LIKE_문법글자는_글자로_다뤄진다() throws Exception {
        // % 와 _ 는 LIKE 에서 "아무 글자" 를 뜻한다. 안 막으면 q=% 하나로 전건이 나온다 —
        // 사용자는 그 글자가 든 상품을 찾아달라고 친 것이다
        mvc.perform(get("/api/retail-gateway/listings").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(get("/api/retail-gateway/listings").param("q", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // 진짜로 그 글자가 제목에 있으면 걸려야 한다
        jdbc.update("INSERT INTO wholesale.product (id, wholesaler_id, product_number, name, category_id, last_variant_seq)"
                + " VALUES (903, 901, 3, '할인 상품', 903, 1)");
        jdbc.update("INSERT INTO wholesale.color_option (id, product_id, color_id) VALUES (904, 903, 901)");
        jdbc.update("INSERT INTO wholesale.variant (id, color_option_id, product_id, size, variant_seq) VALUES (905, 904, 903, 'M', 1)");
        jdbc.update("INSERT INTO wholesale.listing (id, product_id, title, single_piece_allowed, status, season_started_at)"
                + " VALUES (903, 903, '50%% 할인 니트', false, 'ON_SALE', now())");
        jdbc.update("INSERT INTO wholesale.listing_variant (listing_id, variant_id, sale_price, order_limit) VALUES (903, 905, 9000, 0)");

        mvc.perform(get("/api/retail-gateway/listings").param("q", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingId").value(903));
    }

    @Test
    @DisplayName("색상 필터를 걸어도 카드의 색상 수는 안 줄어든다")
    void 색상필터가_카드의_색상수를_바꾸지_않는다() throws Exception {
        // 체리레드로 좁혀도 그 상품이 2색이라는 사실은 그대로여야 한다
        mvc.perform(get("/api/retail-gateway/listings").param("colorIds", "902"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingId").value(901))
                .andExpect(jsonPath("$.data[0].colorCount").value(2));
    }

    @Test
    @DisplayName("상위 카테고리로 걸면 하위 상품까지 나온다")
    void 카테고리는_하위를_포함한다() throws Exception {
        mvc.perform(get("/api/retail-gateway/listings").param("categoryId", "901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingId").value(901));
    }

    @Test
    @DisplayName("상세는 색상 그룹 순 · 사이즈 순으로 정렬해서 준다")
    void 상세는_정렬해서_준다() throws Exception {
        mvc.perform(get("/api/retail-gateway/listings/901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("빈티지 플라워 셔츠"))
                .andExpect(jsonPath("$.data.productNumber").value(1))
                .andExpect(jsonPath("$.data.minSalePrice").value(12500))
                .andExpect(jsonPath("$.data.maxSalePrice").value(13500))
                .andExpect(jsonPath("$.data.listedVariantCount").value(3))
                .andExpect(jsonPath("$.data.totalVariantCount").value(3))
                // 루트 → 리프
                .andExpect(jsonPath("$.data.categoryPath[0].name").value("여성"))
                .andExpect(jsonPath("$.data.categoryPath[2].name").value("상의"))
                .andExpect(jsonPath("$.data.wholesaler.storeUnit").value("2층 24호"))
                // 무채색(그룹 1)이 레드(그룹 2)보다 앞이다
                .andExpect(jsonPath("$.data.colorOptions[0].color.name").value("블랙"))
                .andExpect(jsonPath("$.data.colorOptions[1].color.name").value("체리레드"))
                // 체리레드 안은 S → M 순이다
                .andExpect(jsonPath("$.data.colorOptions[1].variants[0].size").value("S"))
                .andExpect(jsonPath("$.data.colorOptions[1].variants[1].size").value("M"))
                .andExpect(jsonPath("$.data.images[0].sortOrder").value(0));
    }

    @Test
    @DisplayName("시즌이 끝난 상품 상세는 404 다 — 없는 것과 구분하지 않는다")
    void 시즌종료_상세는_404다() throws Exception {
        mvc.perform(get("/api/retail-gateway/listings/902"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(get("/api/retail-gateway/listings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("카테고리는 3단 중첩으로 온다")
    void 카테고리는_중첩으로_온다() throws Exception {
        // V5 시드(MUL-90)의 실제 카테고리가 같이 나오므로 첨자로 짚지 않고 내 것만 걸러 본다.
        // 필터 표현식은 결과를 한 겹 더 배열로 감싸므로 리프는 "한 단계 더 없음" 으로 확인한다
        mvc.perform(get("/api/retail-gateway/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 901)].children[0].id").value(902))
                .andExpect(jsonPath("$.data[?(@.id == 901)].children[0].children[0].id").value(903))
                .andExpect(jsonPath("$.data[?(@.id == 901)].children[0].children[0].children[0]").doesNotExist())
                // 소매는 depth 를 안 쓴다. 도매 화면용 응답과 갈리는 지점이라 못박아 둔다
                .andExpect(jsonPath("$.data[?(@.id == 901)].depth").isEmpty());
    }

    @Test
    @DisplayName("필터 항목의 가격은 게시 중인 상품 기준이다")
    void 필터항목의_가격은_게시중_기준이다() throws Exception {
        mvc.perform(get("/api/retail-gateway/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizes[0]").value("XS"))
                .andExpect(jsonPath("$.data.sizes[6]").value("FREE"))
                // 시즌 끝난 상품의 19000 은 안 들어간다
                .andExpect(jsonPath("$.data.priceRange.min").value(12500))
                .andExpect(jsonPath("$.data.priceRange.max").value(13500));
    }

    @Test
    @DisplayName("여러 값을 반복 파라미터로 보내도 받는다 — 소매가 그 형태로 보낸다")
    void 반복_파라미터를_받는다() throws Exception {
        // 소매의 선언형 클라이언트는 목록을 콤마가 아니라 ids=901&ids=904 로 보낸다.
        // 콤마만 맞춰두면 실제로는 안 붙는데 테스트는 통과하는 상태가 된다
        mvc.perform(get("/api/retail-gateway/variants").param("ids", "901", "904"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.variantId == 901)]").exists())
                .andExpect(jsonPath("$.data[?(@.variantId == 904)]").exists());

        mvc.perform(get("/api/retail-gateway/listings").param("colorIds", "901", "902"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingId").value(901));

        mvc.perform(get("/api/retail-gateway/listings").param("sizes", "S", "M"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].listingId").value(901));
    }

    @Test
    @DisplayName("옵션 배치는 게시가 내려간 것도 주되 orderable 이 false 다")
    void 옵션배치는_게시내려간것도_준다() throws Exception {
        mvc.perform(get("/api/retail-gateway/variants").param("ids", "901,904,999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.variantId == 901)].orderable").value(true))
                .andExpect(jsonPath("$.data[?(@.variantId == 901)].salePrice").value(12500))
                .andExpect(jsonPath("$.data[?(@.variantId == 901)].colorName").value("체리레드"))
                // 시즌이 끝나도 값은 준다. 소매 장바구니 행이 남아 있어서다
                .andExpect(jsonPath("$.data[?(@.variantId == 904)].orderable").value(false))
                .andExpect(jsonPath("$.data[?(@.variantId == 904)].salePrice").value(19000))
                // 없는 옵션은 아예 안 들어간다
                .andExpect(jsonPath("$.data[?(@.variantId == 999999)]").isEmpty());
    }

    @Test
    @DisplayName("잘못된 페이지·가격 범위는 400 이다")
    void 잘못된_입력은_400이다() throws Exception {
        mvc.perform(get("/api/retail-gateway/listings").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/api/retail-gateway/listings").param("priceFrom", "9").param("priceTo", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/api/retail-gateway/variants").param("ids", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
