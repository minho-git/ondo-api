package com.ondo.wholesale.order;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 확정·취소 API 통합 검증 (MUL-47).
 *
 * <p>배분·미송의 세부 규칙은 AllocationValidatorTest·AllocationWriterTest 가,
 * 상세 조립은 OrderQueryIntegrationTest 가 이미 본다. 여기는 상태 전이와
 * 한 트랜잭션의 부수효과, 에러의 HTTP 매핑을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderConfirmCancelIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;
    private long 니트;
    private long 슬랙스;

    @BeforeEach
    void 재료를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "confirm@ondo.test", "9500000009");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9100);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9200, 9201);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        슬랙스 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "슬랙스", 2);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 701L, "행복상회");
        jdbc.update("update wholesale.variant set stock_qty = 10 where id in (?, ?)", 니트, 슬랙스);
    }

    @Test
    void 확정은_한_트랜잭션에_전이와_배분과_미송을_처리하고_상세_스키마로_답한다() throws Exception {
        long orderId = 주문(1, "NEW");
        long 니트라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);
        long 슬랙스라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 슬랙스, 4, 2000, 0, 0);

        mvc.perform(post("/api/wholesale/orders/" + orderId + "/confirm")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"orderItemId": %d, "allocateQty": 3},
                                  {"orderItemId": %d, "allocateQty": 0}
                                ]}""".formatted(니트라인, 슬랙스라인)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.key").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.isConfirmable").value(false))
                .andExpect(jsonPath("$.data.isPackable").value(true))
                .andExpect(jsonPath("$.data.items[0].allocatedQty").value(3))
                .andExpect(jsonPath("$.data.items[0].unallocatedQty").value(2))
                .andExpect(jsonPath("$.data.items[0].backorderQty").value(2))
                .andExpect(jsonPath("$.data.items[1].backorderQty").value(4));

        assertThat(정수("select count(*) from wholesale.packing where order_id = " + orderId)).isEqualTo(1);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(3);
        assertThat(정수("select count(*) from wholesale.backorder where status = 'OPEN'")).isEqualTo(2);
    }

    @Test
    void 전량_0으로도_확정된다() throws Exception {
        long orderId = 주문(1, "NEW");
        long 라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);

        mvc.perform(post("/api/wholesale/orders/" + orderId + "/confirm")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"orderItemId": %d, "allocateQty": 0}]}""".formatted(라인)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.key").value("CONFIRMED"));

        assertThat(정수("select count(*) from wholesale.packing where order_id = " + orderId)).isZero();
        assertThat(정수("select qty from wholesale.backorder where order_item_id = " + 라인)).isEqualTo(5);
    }

    @Test
    void 재확정과_취소된_주문의_확정은_409다() throws Exception {
        long 확정된 = 주문(1, "CONFIRMED");
        long 라인1 = OrderFixture.라인을_넣는다(jdbc, 확정된, 니트, 5, 1000, 5, 0);
        long 취소된 = 주문(2, "CANCELLED");
        long 라인2 = OrderFixture.라인을_넣는다(jdbc, 취소된, 니트, 5, 1000, 0, 0);

        for (long[] pair : new long[][]{{확정된, 라인1}, {취소된, 라인2}}) {
            mvc.perform(post("/api/wholesale/orders/" + pair[0] + "/confirm")
                            .with(TestSecuritySupport.approvedAs(wholesalerId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"items": [{"orderItemId": %d, "allocateQty": 0}]}""".formatted(pair[1])))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
        }
    }

    @Test
    void 라인이_빠진_확정은_400_ORDER_ITEM_MISSING이다() throws Exception {
        long orderId = 주문(1, "NEW");
        long 라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);
        OrderFixture.라인을_넣는다(jdbc, orderId, 슬랙스, 4, 2000, 0, 0);

        mvc.perform(post("/api/wholesale/orders/" + orderId + "/confirm")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"orderItemId": %d, "allocateQty": 1}]}""".formatted(라인)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_ITEM_MISSING"));
    }

    @Test
    void 가용재고가_모자라면_409_INSUFFICIENT_STOCK이고_전이도_없다() throws Exception {
        jdbc.update("update wholesale.variant set stock_qty = 2 where id = ?", 니트);
        long orderId = 주문(1, "NEW");
        long 라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);

        mvc.perform(post("/api/wholesale/orders/" + orderId + "/confirm")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"orderItemId": %d, "allocateQty": 3}]}""".formatted(라인)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(문자열("select status from wholesale.orders where id = " + orderId)).isEqualTo("NEW");
    }

    @Test
    void 남의_주문_확정은_404다() throws Exception {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "other-confirm@ondo.test", "9500000010");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 702L, "남의상회");
        long 남의주문 = OrderFixture.주문을_넣는다(jdbc, 남, 남거래처, 1, "NEW", OffsetDateTime.now());

        mvc.perform(post("/api/wholesale/orders/" + 남의주문 + "/confirm")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": []}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 취소는_신규_주문만_되고_상세_스키마로_답한다() throws Exception {
        long orderId = 주문(1, "NEW");
        OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 0, 0);

        mvc.perform(post("/api/wholesale/orders/" + orderId + "/cancel")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.key").value("CANCELLED"))
                .andExpect(jsonPath("$.data.status.label").value("주문 취소"))
                .andExpect(jsonPath("$.data.isCancellable").value(false));

        assertThat(문자열("select status from wholesale.orders where id = " + orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void 확정된_주문의_취소와_재취소는_409다() throws Exception {
        long 확정된 = 주문(1, "CONFIRMED");
        OrderFixture.라인을_넣는다(jdbc, 확정된, 니트, 5, 1000, 5, 0);
        long 취소된 = 주문(2, "CANCELLED");
        OrderFixture.라인을_넣는다(jdbc, 취소된, 니트, 5, 1000, 0, 0);

        for (long orderId : new long[]{확정된, 취소된}) {
            mvc.perform(post("/api/wholesale/orders/" + orderId + "/cancel")
                            .with(TestSecuritySupport.approvedAs(wholesalerId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
        }
    }

    private long 주문(int orderNumber, String status) {
        return OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, orderNumber, status,
                OffsetDateTime.now());
    }

    private int 정수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String 문자열(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
