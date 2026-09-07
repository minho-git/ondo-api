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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미송 배분(배분 확정) API 통합 검증 (MUL-48).
 *
 * <p>요청 형식·단계별 전건 수집은 BackorderAllocationValidatorTest 가 본다. 여기는
 * 클릭 1회 = batch 1건 + 주문마다 포장 1장, 해소 판정(resolvedBackorderIds), 전체
 * 롤백과 에러의 HTTP 매핑을 본다. 확정 상태는 픽스처가 SQL 로 직접 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackorderAllocationIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long leafId;
    private long colorId;
    private long 티셔츠;
    private long 주문1;
    private long 라인1;
    private long 미송1; // 잔여 3
    private long 주문2;
    private long 라인2;
    private long 미송2; // 잔여 4

    @BeforeEach
    void 미송_두건을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "backorder-alloc@ondo.test", "9500000043");
        leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9186);
        colorId = MasterDataFixture.색상을_넣는다(jdbc, 9284, 9285);
        long partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 745L, "다올몰");
        티셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "티셔츠", 5);
        // 주문1 이 2 를 이미 배분받아 예약 2 — 가용 8
        jdbc.update("update wholesale.variant set stock_qty = 10, reserved_qty = 2 where id = ?", 티셔츠);
        주문1 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 1, "CONFIRMED", OffsetDateTime.now());
        라인1 = OrderFixture.라인을_넣는다(jdbc, 주문1, 티셔츠, 5, 1000, 2, 0);
        미송1 = OrderFixture.미송을_넣는다(jdbc, 라인1, 3, "OPEN");
        주문2 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 2, "CONFIRMED", OffsetDateTime.now());
        라인2 = OrderFixture.라인을_넣는다(jdbc, 주문2, 티셔츠, 4, 1000, 0, 0);
        미송2 = OrderFixture.미송을_넣는다(jdbc, 라인2, 4, "OPEN");
    }

    @Test
    void 여러_주문의_미송에_배분하면_주문마다_포장_카드가_한장씩_생긴다() throws Exception {
        mvc.perform(배분("""
                        { "items": [ { "backorderId": %d, "allocateQty": 2 },
                                     { "backorderId": %d, "allocateQty": 1 } ] }
                        """.formatted(미송1, 미송2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.allocationBatchId").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.packings.length()").value(2))
                .andExpect(jsonPath("$.data.packings[0].orderId").value(주문1))
                .andExpect(jsonPath("$.data.packings[0].orderNumber").value(1))
                .andExpect(jsonPath("$.data.packings[0].status").value("READY"))
                .andExpect(jsonPath("$.data.packings[0].isCancellable").value(true))
                .andExpect(jsonPath("$.data.packings[0].items.length()").value(1))
                .andExpect(jsonPath("$.data.packings[0].items[0].orderItemId").value(라인1))
                .andExpect(jsonPath("$.data.packings[0].items[0].qty").value(2))
                .andExpect(jsonPath("$.data.packings[0].items[0].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data.packings[1].orderId").value(주문2))
                .andExpect(jsonPath("$.data.packings[1].items[0].qty").value(1));

        // 클릭 1회 = batch 1행, 포장 항목이 자기 미송을 가리킨다
        assertThat(정수("select count(*) from wholesale.allocation_batch")).isEqualTo(1);
        assertThat(정수("select count(distinct allocation_batch_id) from wholesale.packing_item")).isEqualTo(1);
        assertThat(정수("select backorder_id from wholesale.packing_item where order_item_id = " + 라인1))
                .isEqualTo((int) 미송1);
        assertThat(정수("select backorder_id from wholesale.packing_item where order_item_id = " + 라인2))
                .isEqualTo((int) 미송2);
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인1)).isEqualTo(4);
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인2)).isEqualTo(1);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 티셔츠)).isEqualTo(5);
    }

    @Test
    void 잔량이_0이_된_미송만_resolvedBackorderIds에_담긴다() throws Exception {
        mvc.perform(배분("""
                        { "items": [ { "backorderId": %d, "allocateQty": 3 },
                                     { "backorderId": %d, "allocateQty": 1 } ] }
                        """.formatted(미송1, 미송2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resolvedBackorderIds.length()").value(1))
                .andExpect(jsonPath("$.data.resolvedBackorderIds[0]").value(미송1));

        assertThat(문자열("select status from wholesale.backorder where id = " + 미송1)).isEqualTo("RESOLVED");
        assertThat(문자열("select status from wholesale.backorder where id = " + 미송2)).isEqualTo("OPEN");
    }

    @Test
    void 부분_배분된_미송은_OPEN으로_남는다() throws Exception {
        mvc.perform(배분("{ \"items\": [ { \"backorderId\": %d, \"allocateQty\": 2 } ] }".formatted(미송2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resolvedBackorderIds.length()").value(0));

        assertThat(문자열("select status from wholesale.backorder where id = " + 미송2)).isEqualTo("OPEN");
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인2)).isEqualTo(2);
    }

    @Test
    void 한_건이라도_걸리면_전체가_롤백된다() throws Exception {
        // 미송2 에 원래 미송량(4) 초과를 섞는다 — 멀쩡한 미송1 쪽도 아무것도 남지 않아야 한다
        mvc.perform(배분("""
                        { "items": [ { "backorderId": %d, "allocateQty": 2 },
                                     { "backorderId": %d, "allocateQty": 5 } ] }
                        """.formatted(미송1, 미송2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_ORDER"));

        assertThat(정수("select count(*) from wholesale.allocation_batch")).isZero();
        assertThat(정수("select count(*) from wholesale.packing")).isZero();
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인1)).isEqualTo(2);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 티셔츠)).isEqualTo(2);
    }

    @Test
    void 남의_미송은_404다() throws Exception {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "backorder-alloc-other@ondo.test", "9500000044");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 746L, "남의상회");
        long 남변형 = OrderFixture.상품_변형을_넣는다(jdbc, 남, leafId, colorId, "남의니트", 1);
        long 남주문 = OrderFixture.주문을_넣는다(jdbc, 남, 남거래처, 1, "CONFIRMED", OffsetDateTime.now());
        long 남라인 = OrderFixture.라인을_넣는다(jdbc, 남주문, 남변형, 5, 1000, 0, 0);
        long 남미송 = OrderFixture.미송을_넣는다(jdbc, 남라인, 5, "OPEN");

        mvc.perform(배분("{ \"items\": [ { \"backorderId\": %d, \"allocateQty\": 1 } ] }".formatted(남미송)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 가용재고_합계_초과는_409_INSUFFICIENT_STOCK이다() throws Exception {
        // 가용을 3 으로 줄인다 — 각자는 잔여 안(2·2)이지만 합 4 가 넘친다
        jdbc.update("update wholesale.variant set stock_qty = 5, reserved_qty = 2 where id = ?", 티셔츠);

        mvc.perform(배분("""
                        { "items": [ { "backorderId": %d, "allocateQty": 2 },
                                     { "backorderId": %d, "allocateQty": 2 } ] }
                        """.formatted(미송1, 미송2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    private MockHttpServletRequestBuilder 배분(String body) {
        return post("/api/wholesale/backorders/allocations")
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private int 정수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String 문자열(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
