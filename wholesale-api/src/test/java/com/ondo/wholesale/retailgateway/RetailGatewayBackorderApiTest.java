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
 * 소매 미송 조회 API (MUL-97).
 *
 * <p>{@link RetailGatewayListingApiTest} 와 같은 이유로 로컬 시드에 안 기댄다 —
 * 데이터를 이 안에서 직접 넣고 {@code @Transactional} 로 롤백한다.
 *
 * <p>여기서 확인할 건 <b>무엇이 안 나오는지</b>가 절반이다. 미송은 소매처별로 잘린
 * 데이터라 한 줄만 새도 남의 거래 내역이 넘어간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RetailGatewayBackorderApiTest extends PostgresTestSupport {

    /** 우리 소매처. 이 소매처의 미송만 나와야 한다. */
    private static final long 우리 = 7001L;

    /** 남의 소매처. 같은 도매처와 거래하지만 우리 응답에 섞이면 안 된다. */
    private static final long 남 = 7002L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void 데이터를_넣는다() {
        jdbc.update("INSERT INTO common.category (id, parent_id, name, depth, sort_order) VALUES (911, NULL, '여성', 1, 1)");
        jdbc.update("INSERT INTO common.color_group (id, name, sort_order) VALUES (911, '무채색', 1)");
        jdbc.update("INSERT INTO common.color (id, group_id, name, hex, sort_order) VALUES (911, 911, '네이비', '#1F2A44', 1)");

        jdbc.update("""
                INSERT INTO wholesale.wholesaler
                    (id, email, password_hash, biz_reg_no, biz_name, biz_owner_name, approval_status, last_product_seq)
                VALUES (911, 'bo-test@ondo.test', 'x', '9990000011', '무드온', '테스트', 'APPROVED', 2)
                """);

        // 상품 A — 게시 중. 예상 입고일이 적혀 있다
        상품(911, 1, "와이드 데님 팬츠", 911, "M", "2026-09-10", "공장 재입고 예정");
        jdbc.update("INSERT INTO wholesale.listing (id, product_id, title, single_piece_allowed, status, season_started_at)"
                + " VALUES (911, 911, '와이드 데님 팬츠', false, 'ON_SALE', now())");

        // 상품 B — 게시글이 지워졌다. 미송은 그래도 남아야 한다
        상품(912, 2, "단종된 니트", 912, "L", null, null);
        jdbc.update("INSERT INTO wholesale.listing (id, product_id, title, single_piece_allowed, status, season_started_at, deleted_at)"
                + " VALUES (912, 912, '단종된 니트', false, 'ON_SALE', now(), now())");

        거래처(911, 우리, "우리소매");
        거래처(912, 남, "남의소매");

        // 주문 911 — 우리 것. 제일 오래됐다
        주문(911, 1, 911, 5001L, "3 day");
        // 주문 912 — 우리 것. 더 최근이다
        주문(912, 2, 911, 5008L, "1 day");
        // 주문 913 — 남의 것
        주문(913, 3, 912, 5099L, "2 day");
        // 주문 914 — 우리 것이지만 도매가 자기 화면에서 직접 넣었다. 소매에 주문서가 없다
        주문(914, 4, 911, null, "4 day");

        // 미송 911 — 우리 · OPEN · 오래된 주문. 첫 줄이어야 한다
        미송(911, 911, 911, 4, "OPEN");
        // 미송 912 — 우리 · OPEN · 지워진 게시글의 상품
        미송(912, 912, 912, 1, "OPEN");
        // 미송 913 — 우리 · 이미 해소됨. 안 나와야 한다
        미송(913, 912, 911, 2, "RESOLVED");
        // 미송 914 — 남의 것. 안 나와야 한다
        미송(914, 913, 911, 9, "OPEN");
        // 미송 915 — 도매 직접 주문. 소매가 주문번호를 못 채우므로 안 나와야 한다
        미송(915, 914, 911, 7, "OPEN");
    }

    @Test
    @DisplayName("내 미송만 오래된 순으로 나온다")
    void 내_미송만_오래된순으로_나온다() throws Exception {
        mvc.perform(get("/api/retail-gateway/backorders").param("retailerId", String.valueOf(우리)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].backorderId").value(911))
                .andExpect(jsonPath("$.data[0].retailOrderId").value(5001))
                .andExpect(jsonPath("$.data[0].wholesaler.name").value("무드온"))
                .andExpect(jsonPath("$.data[0].title").value("와이드 데님 팬츠"))
                .andExpect(jsonPath("$.data[0].colorName").value("네이비"))
                .andExpect(jsonPath("$.data[0].size").value("M"))
                .andExpect(jsonPath("$.data[0].qty").value(4))
                .andExpect(jsonPath("$.data[1].backorderId").value(912))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    @DisplayName("해소된 것 · 남의 것 · 도매 직접 주문은 안 나온다")
    void 안_보여야_할_것은_안_나온다() throws Exception {
        mvc.perform(get("/api/retail-gateway/backorders").param("retailerId", String.valueOf(우리)))
                .andExpect(status().isOk())
                // 913 해소됨 · 914 남의 것 · 915 도매 직접 주문
                .andExpect(jsonPath("$.data[?(@.backorderId == 913)]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.backorderId == 914)]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.backorderId == 915)]").isEmpty());
    }

    @Test
    @DisplayName("남의 소매처로 부르면 자기 것만 나온다")
    void 소매처마다_다른것을_본다() throws Exception {
        mvc.perform(get("/api/retail-gateway/backorders").param("retailerId", String.valueOf(남)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].backorderId").value(914))
                .andExpect(jsonPath("$.data[0].qty").value(9));
    }

    @Test
    @DisplayName("예상 입고일은 SKU 에 적힌 값이다. 안 적었으면 null 이다")
    void 예상_입고일은_SKU_에서_온다() throws Exception {
        mvc.perform(get("/api/retail-gateway/backorders").param("retailerId", String.valueOf(우리)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].expectedInboundDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data[0].expectedInboundReason").value("공장 재입고 예정"))
                // 안 적은 SKU 는 비어 있다. 화면이 "도매처가 안내할 예정" 을 그리는 분기다
                .andExpect(jsonPath("$.data[1].expectedInboundDate").doesNotExist())
                .andExpect(jsonPath("$.data[1].expectedInboundReason").doesNotExist());
    }

    @Test
    @DisplayName("게시글이 지워져도 미송은 남는다. 상품명은 품명으로 대신한다")
    void 게시글이_지워져도_미송은_남는다() throws Exception {
        mvc.perform(get("/api/retail-gateway/backorders").param("retailerId", String.valueOf(우리)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].backorderId").value(912))
                // 게시글이 없으니 상품 상세로 넘어갈 수 없다
                .andExpect(jsonPath("$.data[1].listingId").doesNotExist())
                .andExpect(jsonPath("$.data[1].title").value("단종된 니트"));
    }

    @Test
    @DisplayName("size 가 100 을 넘으면 400 이다")
    void 너무_큰_size_는_거절한다() throws Exception {
        mvc.perform(get("/api/retail-gateway/backorders")
                        .param("retailerId", String.valueOf(우리))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── 데이터 심기 ─────────────────────────────────────────────

    /** 상품 하나 + 색상옵션 + SKU 한 개. 미송이 걸릴 자리만 만든다. */
    private void 상품(long productId, int productNumber, String name, long variantId,
                     String size, String expectedDate, String expectedReason) {
        jdbc.update("INSERT INTO wholesale.product (id, wholesaler_id, product_number, name, category_id, last_variant_seq)"
                + " VALUES (?, 911, ?, ?, 911, 1)", productId, productNumber, name);
        jdbc.update("INSERT INTO wholesale.color_option (id, product_id, color_id) VALUES (?, ?, 911)",
                productId, productId);
        jdbc.update("INSERT INTO wholesale.variant"
                        + " (id, color_option_id, product_id, size, variant_seq, expected_inbound_date, expected_inbound_reason)"
                        + " VALUES (?, ?, ?, ?, 1, CAST(? AS date), ?)",
                variantId, productId, productId, size, expectedDate, expectedReason);
    }

    private void 거래처(long id, long retailerId, String retailerName) {
        jdbc.update("INSERT INTO wholesale.partner (id, wholesaler_id, retailer_id, retailer_name)"
                + " VALUES (?, 911, ?, ?)", id, retailerId, retailerName);
    }

    /** @param retailOrderId null 이면 도매가 자기 화면에서 직접 넣은 주문이다 */
    private void 주문(long id, int orderNumber, long partnerId, Long retailOrderId, String 며칠전) {
        jdbc.update("INSERT INTO wholesale.orders"
                        + " (id, order_number, retail_order_id, partner_id, wholesaler_id, status,"
                        + "  payment_term, receive_method, ordered_at)"
                        + " VALUES (?, ?, ?, ?, 911, 'NEW', 'CASH', 'AGENT', now() - CAST(? AS interval))",
                id, orderNumber, retailOrderId, partnerId, 며칠전);
    }

    private void 미송(long id, long orderId, long variantId, int qty, String status) {
        long orderItemId = id;
        jdbc.update("INSERT INTO wholesale.order_item (id, order_id, variant_id, qty, unit_price)"
                + " VALUES (?, ?, ?, ?, 12000)", orderItemId, orderId, variantId, qty);
        jdbc.update("INSERT INTO wholesale.backorder (id, order_item_id, qty, status)"
                + " VALUES (?, ?, ?, ?)", id, orderItemId, qty, status);
    }
}
