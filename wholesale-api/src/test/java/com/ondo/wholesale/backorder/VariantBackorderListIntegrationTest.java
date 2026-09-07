package com.ondo.wholesale.backorder;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SKU별 미송 목록 + 요약(아코디언 펼침) API 통합 검증 (MUL-48).
 *
 * <p>data(FIFO)와 stats(잔여합·주문수·소매처수·금액·최초/최근 주문일)를 한 응답에 내린다.
 * 봉투는 meta 없이 data + stats 다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VariantBackorderListIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;
    private long 티셔츠;
    private int nextOrderNumber = 1;

    @BeforeEach
    void 미송_SKU를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "variant-backorders@ondo.test", "9500000042");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9183);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9282, 9283);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 743L, "다올몰");
        티셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "티셔츠", 3);
        jdbc.update("update wholesale.variant set stock_qty = 9, reserved_qty = 2 where id = ?", 티셔츠);
    }

    @Test
    void SKU별_미송을_FIFO로_data와_stats에_함께_내린다() throws Exception {
        // 늦게 생긴 미송을 먼저 심는다 — 정렬이 생성 시각(FIFO)임을 드러내기 위해서다
        long 새주문 = 주문을_넣는다();
        long 새라인 = OrderFixture.라인을_넣는다(jdbc, 새주문, 티셔츠, 2, 1000, 0, 0);
        long 새미송 = OrderFixture.미송을_넣는다(jdbc, 새라인, 2, "OPEN");
        // 3일 전 미송 — 원래 4 중 2를 이미 배분받아 잔여 2 (qty 와 remainingQty 가 다르다)
        long 옛주문 = 주문을_넣는다();
        long 옛라인 = OrderFixture.라인을_넣는다(jdbc, 옛주문, 티셔츠, 5, 1000, 3, 0);
        long 옛미송 = OrderFixture.미송을_넣는다(jdbc, 옛라인, 4, "OPEN");
        jdbc.update("update wholesale.backorder set created_at = now() - interval '3 day' where id = ?", 옛미송);

        mvc.perform(펼침조회(티셔츠))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(옛미송))
                .andExpect(jsonPath("$.data[0].orderId").value(옛주문))
                .andExpect(jsonPath("$.data[0].orderItemId").value(옛라인))
                .andExpect(jsonPath("$.data[0].elapsedDays").value(3))
                .andExpect(jsonPath("$.data[0].retailerId").value(743))
                .andExpect(jsonPath("$.data[0].retailerName").value("다올몰"))
                .andExpect(jsonPath("$.data[0].qty").value(4))
                .andExpect(jsonPath("$.data[0].remainingQty").value(2))
                .andExpect(jsonPath("$.data[0].unitPrice").value(1000))
                .andExpect(jsonPath("$.data[1].id").value(새미송))
                .andExpect(jsonPath("$.stats.variantId").value(티셔츠))
                .andExpect(jsonPath("$.stats.backorderQty").value(4))
                .andExpect(jsonPath("$.stats.availableQty").value(7));
    }

    @Test
    void backorderAmount는_주문시점_단가로_계산한다() throws Exception {
        long 주문1 = 주문을_넣는다();
        long 라인1 = OrderFixture.라인을_넣는다(jdbc, 주문1, 티셔츠, 5, 1000, 2, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인1, 3, "OPEN");
        long 주문2 = 주문을_넣는다();
        long 라인2 = OrderFixture.라인을_넣는다(jdbc, 주문2, 티셔츠, 4, 2000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인2, 4, "OPEN");

        mvc.perform(펼침조회(티셔츠))
                .andExpect(status().isOk())
                // 3 × 1000 + 4 × 2000 — 잔여 × 주문 시점 단가의 합
                .andExpect(jsonPath("$.stats.backorderAmount").value(11000));
    }

    @Test
    void orderCount는_주문수_retailerCount는_소매처수다() throws Exception {
        // 주문 하나에 같은 SKU 라인 둘 — 미송 2건이지만 주문은 1건으로 센다
        long 주문1 = 주문을_넣는다();
        long 라인1 = OrderFixture.라인을_넣는다(jdbc, 주문1, 티셔츠, 3, 1000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인1, 3, "OPEN");
        long 라인2 = OrderFixture.라인을_넣는다(jdbc, 주문1, 티셔츠, 2, 1000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인2, 2, "OPEN");
        // 다른 소매처의 주문 하나
        long 거래처2 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 744L, "행복상회");
        long 주문2 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 거래처2, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long 라인3 = OrderFixture.라인을_넣는다(jdbc, 주문2, 티셔츠, 1, 1000, 0, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인3, 1, "OPEN");

        mvc.perform(펼침조회(티셔츠))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.stats.orderCount").value(2))
                .andExpect(jsonPath("$.stats.retailerCount").value(2));
    }

    @Test
    void 없는_variant는_404다() throws Exception {
        mvc.perform(펼침조회(999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void OPEN미송이_없으면_빈목록과_0집계다() throws Exception {
        long 주문 = 주문을_넣는다();
        long 라인 = OrderFixture.라인을_넣는다(jdbc, 주문, 티셔츠, 5, 1000, 5, 0);
        OrderFixture.미송을_넣는다(jdbc, 라인, 3, "RESOLVED");

        mvc.perform(펼침조회(티셔츠))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.stats.variantId").value(티셔츠))
                .andExpect(jsonPath("$.stats.backorderQty").value(0))
                .andExpect(jsonPath("$.stats.orderCount").value(0))
                .andExpect(jsonPath("$.stats.retailerCount").value(0))
                .andExpect(jsonPath("$.stats.backorderAmount").value(0))
                .andExpect(jsonPath("$.stats.availableQty").value(7))
                .andExpect(jsonPath("$.stats.firstOrderedAt").isEmpty())
                .andExpect(jsonPath("$.stats.lastOrderedAt").isEmpty());
    }

    private long 주문을_넣는다() {
        return OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder 펼침조회(long variantId) {
        return get("/api/wholesale/variants/" + variantId + "/backorders")
                .with(TestSecuritySupport.approvedAs(wholesalerId));
    }
}
