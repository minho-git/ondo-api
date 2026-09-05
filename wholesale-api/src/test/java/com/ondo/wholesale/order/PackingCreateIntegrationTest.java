package com.ondo.wholesale.order;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포장 준비(추가 배분) API 통합 검증 (MUL-47).
 *
 * <p>요청 형식 검증은 AllocationValidatorTest 가 본다. 여기는 미송 연결·해소와
 * 카드 응답, 에러의 HTTP 매핑을 본다. 확정 상태는 픽스처가 SQL 로 직접 만든다
 * (라인 allocated 2/5, OPEN 미송 3, variant 예약 2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PackingCreateIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;
    private long 니트;
    private long orderId;
    private long 라인;
    private long 미송;

    @BeforeEach
    void 확정_주문을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "packing@ondo.test", "9500000011");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9100);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9200, 9201);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 701L, "행복상회");
        jdbc.update("update wholesale.variant set stock_qty = 10, reserved_qty = 2 where id = ?", 니트);
        orderId = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 1, "CONFIRMED",
                OffsetDateTime.now());
        라인 = OrderFixture.라인을_넣는다(jdbc, orderId, 니트, 5, 1000, 2, 0);
        미송 = OrderFixture.미송을_넣는다(jdbc, 라인, 3, "OPEN");
    }

    @Test
    void 포장_카드_한장을_만들고_미송을_연결한다() throws Exception {
        mvc.perform(포장준비("{\"items\": [{\"orderItemId\": %d, \"allocateQty\": 2}]}".formatted(라인)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.outboundId").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].orderItemId").value(라인))
                .andExpect(jsonPath("$.data.items[0].qty").value(2))
                .andExpect(jsonPath("$.data.items[0].productNumber").value(1))
                .andExpect(jsonPath("$.data.items[0].variantNumber").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("니트"))
                .andExpect(jsonPath("$.data.items[0].color").value("블랙"))
                .andExpect(jsonPath("$.data.items[0].size").value("FREE"));

        assertThat(정수("select backorder_id from wholesale.packing_item where order_item_id = " + 라인))
                .isEqualTo((int) 미송);
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인)).isEqualTo(4);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(4);
        // 잔량 1이 남아 미송은 아직 OPEN 이다
        assertThat(문자열("select status from wholesale.backorder where id = " + 미송)).isEqualTo("OPEN");
    }

    @Test
    void 잔량을_전부_배분하면_미송이_해소된다() throws Exception {
        mvc.perform(포장준비("{\"items\": [{\"orderItemId\": %d, \"allocateQty\": 3}]}".formatted(라인)))
                .andExpect(status().isCreated());

        assertThat(문자열("select status from wholesale.backorder where id = " + 미송)).isEqualTo("RESOLVED");
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인)).isEqualTo(5);
    }

    @Test
    void 잔량을_넘는_배분은_409_ALLOCATION_EXCEEDS_REMAINING이다() throws Exception {
        mvc.perform(포장준비("{\"items\": [{\"orderItemId\": %d, \"allocateQty\": 4}]}".formatted(라인)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_REMAINING"));
    }

    @Test
    void 신규나_취소된_주문의_포장준비는_409다() throws Exception {
        long 신규 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 2, "NEW", OffsetDateTime.now());
        long 신규라인 = OrderFixture.라인을_넣는다(jdbc, 신규, 니트, 3, 1000, 0, 0);
        long 취소 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 3, "CANCELLED", OffsetDateTime.now());
        long 취소라인 = OrderFixture.라인을_넣는다(jdbc, 취소, 니트, 3, 1000, 0, 0);

        for (long[] pair : new long[][]{{신규, 신규라인}, {취소, 취소라인}}) {
            mvc.perform(post("/api/wholesale/orders/" + pair[0] + "/packings")
                            .with(TestSecuritySupport.approvedAs(wholesalerId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\": [{\"orderItemId\": %d, \"allocateQty\": 1}]}".formatted(pair[1])))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
        }
    }

    @Test
    void 남의_주문의_포장준비는_404다() throws Exception {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "other-packing@ondo.test", "9500000012");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 702L, "남의상회");
        long 남의주문 = OrderFixture.주문을_넣는다(jdbc, 남, 남거래처, 1, "CONFIRMED", OffsetDateTime.now());

        mvc.perform(post("/api/wholesale/orders/" + 남의주문 + "/packings")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"orderItemId\": 1, \"allocateQty\": 1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void allocateQty_0은_400_INVARIANT_VIOLATED다() throws Exception {
        mvc.perform(포장준비("{\"items\": [{\"orderItemId\": %d, \"allocateQty\": 0}]}".formatted(라인)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder 포장준비(String body) {
        return post("/api/wholesale/orders/" + orderId + "/packings")
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
