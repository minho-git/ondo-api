package com.ondo.wholesale.backorder;

import com.jayway.jsonpath.JsonPath;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미송 SKU 목록(아코디언 헤더) API 통합 검증 (MUL-48).
 *
 * <p>OPEN 미송만 SKU 단위로 집계한다 — backorderQty = Σ(주문수량 − 배분수량).
 * 확정 상태·미송 행은 픽스처가 SQL 로 직접 심는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackorderSkuListIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long leafId;
    private long colorId;
    private long partnerId;
    private int nextOrderNumber = 1;
    private int nextProductNumber = 1;

    @BeforeEach
    void 도매처와_마스터를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "backorder-sku-list@ondo.test", "9500000040");
        leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9180);
        colorId = MasterDataFixture.색상을_넣는다(jdbc, 9280, 9281);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 741L, "다올몰");
    }

    @Test
    void size가_100을_넘으면_400이다() throws Exception {
        mvc.perform(목록조회().param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void 모르는_정렬키는_400이다() throws Exception {
        mvc.perform(목록조회().param("sort", "productName,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void 기본_정렬은_최근에_미송이_쌓인_순_내림차순이다() throws Exception {
        // 잔여가 큰(6) 티셔츠의 미송을 이틀 전으로 밀어둔다 — 수량 정렬이었다면 티셔츠가 먼저다
        long 티셔츠 = 미송_SKU를_심는다("티셔츠", 8, 2, 10);   // 잔여 6, 이틀 전
        미송_생성시각을_민다(티셔츠, 2);
        미송_SKU를_심는다("니트", 5, 2, 10);                  // 잔여 3, 방금

        mvc.perform(목록조회())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productName").value("니트"))
                .andExpect(jsonPath("$.data[0].backorderQty").value(3))
                .andExpect(jsonPath("$.data[1].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data[1].backorderQty").value(6))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void backorderQty_정렬은_옵션으로_동작한다() throws Exception {
        long 티셔츠 = 미송_SKU를_심는다("티셔츠", 8, 2, 10);   // 잔여 6, 이틀 전
        미송_생성시각을_민다(티셔츠, 2);
        미송_SKU를_심는다("니트", 5, 2, 10);                  // 잔여 3, 방금

        mvc.perform(목록조회().param("sort", "backorderQty,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data[0].backorderQty").value(6))
                .andExpect(jsonPath("$.data[1].productName").value("니트"));
    }

    @Test
    void 미송이_남은_SKU만_잔여합계와_함께_내린다() throws Exception {
        // 같은 SKU 를 기다리는 주문 두 건 — 잔여 4 + 2 가 한 행으로 합쳐진다
        long 티셔츠 = 미송_SKU를_심는다("티셔츠", 6, 2, 10);
        long 주문2 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long 라인2 = OrderFixture.라인을_넣는다(jdbc, 주문2, 티셔츠, 2, 1000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인2, 2, "OPEN");
        // 미송이 없는 SKU 는 목록에 없다
        OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "슬랙스", nextProductNumber++);

        mvc.perform(목록조회())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].variantId").value(티셔츠))
                .andExpect(jsonPath("$.data[0].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data[0].color").value("블랙"))
                .andExpect(jsonPath("$.data[0].size").value("FREE"))
                .andExpect(jsonPath("$.data[0].backorderQty").value(6))
                .andExpect(jsonPath("$.data[0].availableQty").value(8))
                .andExpect(jsonPath("$.data[0].expectedInboundDate").isEmpty());
    }

    @Test
    void 미송_SKU_응답에_최근_미송_발생_시각이_실린다() throws Exception {
        // 미송 두 건 — 방금 것과 사흘 전 것. 응답에는 더 최근 시각이 실려야 한다
        long 티셔츠 = 미송_SKU를_심는다("티셔츠", 6, 2, 10);
        long 주문2 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long 라인2 = OrderFixture.라인을_넣는다(jdbc, 주문2, 티셔츠, 2, 1000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인2, 2, "OPEN");
        jdbc.update("""
                update wholesale.backorder set created_at = now() - make_interval(days => 3)
                where order_item_id = ?
                """, 라인2);

        String body = mvc.perform(목록조회())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].latestBackorderedAt").exists())
                .andReturn().getResponse().getContentAsString();

        OffsetDateTime latest = OffsetDateTime.parse(
                JsonPath.<String>read(body, "$.data[0].latestBackorderedAt"));
        assertThat(latest).isAfter(OffsetDateTime.now().minusMinutes(5));
    }

    @Test
    void 전부_해소된_SKU는_목록에서_빠진다() throws Exception {
        미송_SKU를_심는다("티셔츠", 5, 2, 10);
        // 전량 배분돼 미송이 해소된 SKU
        long 니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", nextProductNumber++);
        long 주문 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long 라인 = OrderFixture.라인을_넣는다(jdbc, 주문, 니트, 5, 1000, 5, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인, 3, "RESOLVED");

        mvc.perform(목록조회())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("티셔츠"));
    }

    @Test
    void 검색어는_상품명_부분일치다() throws Exception {
        미송_SKU를_심는다("오버핏 티셔츠", 5, 2, 10);
        미송_SKU를_심는다("니트", 4, 1, 10);

        mvc.perform(목록조회().param("q", "티셔"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("오버핏 티셔츠"));
    }

    @Test
    void 다른_도매처의_미송은_안_보인다() throws Exception {
        미송_SKU를_심는다("티셔츠", 5, 2, 10);
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "backorder-sku-other@ondo.test", "9500000041");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 742L, "남의상회");
        long 남변형 = OrderFixture.상품_변형을_넣는다(jdbc, 남, leafId, colorId, "남의니트", 1);
        long 남주문 = OrderFixture.주문을_넣는다(jdbc, 남, 남거래처, 1, "CONFIRMED", OffsetDateTime.now());
        long 남라인 = OrderFixture.라인을_넣는다(jdbc, 남주문, 남변형, 5, 1000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 남라인, 5, "OPEN");

        mvc.perform(목록조회())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("티셔츠"));
    }

    /** 주문(qty, allocated)과 OPEN 미송(qty − allocated)이 딸린 SKU 하나를 심는다. 가용재고 = stock − 0. */
    private long 미송_SKU를_심는다(String name, int qty, int allocated, int stock) {
        long variantId = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId,
                name, nextProductNumber++);
        jdbc.update("update wholesale.variant set stock_qty = ?, reserved_qty = ? where id = ?",
                stock, allocated, variantId);
        long orderId = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long lineId = OrderFixture.라인을_넣는다(jdbc, orderId, variantId, qty, 1000, allocated, 0);
        OrderFixture.미송을_넣는다(jdbc, lineId, qty - allocated, "OPEN");
        return variantId;
    }

    /** 그 SKU 에 딸린 미송의 생성 시각을 며칠 전으로 민다 — 최근 미송 정렬 검증용. */
    private void 미송_생성시각을_민다(long variantId, int daysAgo) {
        jdbc.update("""
                update wholesale.backorder set created_at = now() - make_interval(days => ?)
                where order_item_id in (select id from wholesale.order_item where variant_id = ?)
                """, daysAgo, variantId);
    }

    private MockHttpServletRequestBuilder 목록조회() {
        return get("/api/wholesale/backorders/variants")
                .with(TestSecuritySupport.approvedAs(wholesalerId));
    }
}
