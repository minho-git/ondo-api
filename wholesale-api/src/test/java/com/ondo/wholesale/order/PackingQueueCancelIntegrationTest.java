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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포장 대기열 조회·배분 취소 API 통합 검증 (MUL-47).
 *
 * <p>배분·미송 생성 규칙은 AllocationWriterTest·PackingCreateIntegrationTest 가 본다.
 * 여기는 대기열 표시 규칙(취소 카드 숨김·isCancellable)과 취소의 되돌림
 * (배분·예약 감소, 미송 부활 세 갈래)을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PackingQueueCancelIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long partnerId;
    private long 니트;
    private long orderId;
    private long batchId;

    @BeforeEach
    void 확정_주문을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "queue@ondo.test", "9500000013");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9100);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9200, 9201);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        partnerId = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 701L, "행복상회");
        jdbc.update("update wholesale.variant set stock_qty = 10 where id = ?", 니트);
        orderId = OrderFixture.주문을_넣는다(jdbc, wholesalerId, partnerId, 1, "CONFIRMED",
                OffsetDateTime.now());
        batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);
    }

    @Test
    void 대기열은_포장별_항목과_취소_가능_여부를_내린다() throws Exception {
        long 라인 = 라인(5, 3);
        long ready = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, ready, 라인, null, batchId, 2, false);
        long packed = OrderFixture.포장을_넣는다(jdbc, orderId, "PACKED");
        OrderFixture.포장항목을_넣는다(jdbc, packed, 라인, null, batchId, 1, false);

        mvc.perform(대기열())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(ready))
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].outboundId").isEmpty())
                .andExpect(jsonPath("$.data[0].isCancellable").value(true))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].items[0].qty").value(2))
                .andExpect(jsonPath("$.data[0].items[0].productName").value("니트"))
                .andExpect(jsonPath("$.data[0].items[0].color").value("블랙"))
                .andExpect(jsonPath("$.data[1].id").value(packed))
                .andExpect(jsonPath("$.data[1].isCancellable").value(false));
    }

    @Test
    void 전_항목이_배분취소된_포장은_대기열에_나오지_않는다() throws Exception {
        long 라인 = 라인(5, 0);
        long 취소된 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 취소된, 라인, null, batchId, 2, true);

        mvc.perform(대기열())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void status_파라미터로_거르고_미정의_값은_400이다() throws Exception {
        long 라인 = 라인(5, 3);
        long ready = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, ready, 라인, null, batchId, 2, false);
        long packed = OrderFixture.포장을_넣는다(jdbc, orderId, "PACKED");
        OrderFixture.포장항목을_넣는다(jdbc, packed, 라인, null, batchId, 1, false);

        mvc.perform(get("/api/wholesale/orders/" + orderId + "/packings?status=READY")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ready));

        mvc.perform(get("/api/wholesale/orders/" + orderId + "/packings?status=NOPE")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void sort_desc는_최신_포장이_먼저고_미정의_sort는_400이다() throws Exception {
        long 라인 = 라인(5, 3);
        long 먼저 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 먼저, 라인, null, batchId, 1, false);
        long 나중 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 나중, 라인, null, batchId, 2, false);

        mvc.perform(get("/api/wholesale/orders/" + orderId + "/packings?sort=createdAt,desc")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].id").value(나중))
                .andExpect(jsonPath("$.data[1].id").value(먼저));

        mvc.perform(get("/api/wholesale/orders/" + orderId + "/packings?sort=hack")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 남의_주문_대기열은_404다() throws Exception {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "other-queue@ondo.test", "9500000014");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 702L, "남의상회");
        long 남의주문 = OrderFixture.주문을_넣는다(jdbc, 남, 남거래처, 1, "CONFIRMED", OffsetDateTime.now());

        mvc.perform(get("/api/wholesale/orders/" + 남의주문 + "/packings")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 배분취소는_배분과_예약을_되돌리고_해소했던_미송을_되살린다() throws Exception {
        // 확정 2 + 포장 준비 3(미송 해소) 이 끝난 상태: allocated 5, 예약 5, 미송 RESOLVED
        long 라인 = 라인(5, 5);
        jdbc.update("update wholesale.variant set reserved_qty = 5 where id = ?", 니트);
        long 미송 = OrderFixture.미송을_넣는다(jdbc, 라인, 3, "RESOLVED");
        long 포장 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장, 라인, 미송, batchId, 3, false);

        mvc.perform(취소(포장)).andExpect(status().isNoContent());

        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인)).isEqualTo(2);
        assertThat(정수("select reserved_qty from wholesale.variant where id = " + 니트)).isEqualTo(2);
        assertThat(문자열("select status from wholesale.backorder where id = " + 미송)).isEqualTo("OPEN");
        assertThat(정수("select count(*) from wholesale.packing_item where packing_id = " + 포장
                + " and deleted_at is not null")).isEqualTo(1);
    }

    @Test
    void 연결도_OPEN_미송도_없으면_새_미송이_생긴다() throws Exception {
        // 확정 때 전량 배분해 미송이 애초에 없던 라인
        long 라인 = 라인(5, 5);
        jdbc.update("update wholesale.variant set reserved_qty = 5 where id = ?", 니트);
        long 포장 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장, 라인, null, batchId, 5, false);

        mvc.perform(취소(포장)).andExpect(status().isNoContent());

        assertThat(정수("select count(*) from wholesale.backorder where order_item_id = " + 라인)).isEqualTo(1);
        assertThat(정수("select qty from wholesale.backorder where order_item_id = " + 라인)).isEqualTo(5);
        assertThat(문자열("select status from wholesale.backorder where order_item_id = " + 라인)).isEqualTo("OPEN");
    }

    @Test
    void OPEN_미송이_이미_있으면_새로_만들지_않는다() throws Exception {
        // 확정 배분 2 에 잔량 미송 3 이 살아 있는 라인 — 확정분 포장을 취소해도 미송은 한 건이다
        long 라인 = 라인(5, 2);
        jdbc.update("update wholesale.variant set reserved_qty = 2 where id = ?", 니트);
        long 미송 = OrderFixture.미송을_넣는다(jdbc, 라인, 3, "OPEN");
        long 포장 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장, 라인, null, batchId, 2, false);

        mvc.perform(취소(포장)).andExpect(status().isNoContent());

        assertThat(정수("select count(*) from wholesale.backorder where order_item_id = " + 라인)).isEqualTo(1);
        assertThat(정수("select qty from wholesale.backorder where id = " + 미송)).isEqualTo(3);
        assertThat(정수("select allocated_qty from wholesale.order_item where id = " + 라인)).isZero();
    }

    @Test
    void 이미_취소된_포장의_재취소는_404다() throws Exception {
        long 라인 = 라인(5, 0);
        long 포장 = OrderFixture.포장을_넣는다(jdbc, orderId, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장, 라인, null, batchId, 2, true);

        mvc.perform(취소(포장))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void PACKED_포장의_취소는_409_DOCUMENT_FINALIZED다() throws Exception {
        long 라인 = 라인(5, 2);
        long 포장 = OrderFixture.포장을_넣는다(jdbc, orderId, "PACKED");
        OrderFixture.포장항목을_넣는다(jdbc, 포장, 라인, null, batchId, 2, false);

        mvc.perform(취소(포장))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_FINALIZED"));
    }

    @Test
    void 남의_포장_취소는_404다() throws Exception {
        long 남 = MasterDataFixture.도매처를_넣는다(jdbc, "other-cancel@ondo.test", "9500000015");
        long 남거래처 = OrderFixture.거래처를_넣는다(jdbc, 남, 702L, "남의상회");
        long 남의주문 = OrderFixture.주문을_넣는다(jdbc, 남, 남거래처, 1, "CONFIRMED", OffsetDateTime.now());
        long 남의라인 = OrderFixture.라인을_넣는다(jdbc, 남의주문, 니트, 3, 1000, 1, 0);
        long 남의배치 = OrderFixture.배분_배치를_넣는다(jdbc, 남);
        long 남의포장 = OrderFixture.포장을_넣는다(jdbc, 남의주문, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 남의포장, 남의라인, null, 남의배치, 1, false);

        mvc.perform(취소(남의포장))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private long 라인(int qty, int allocated) {
        return OrderFixture.라인을_넣는다(jdbc, orderId, 니트, qty, 1000, allocated, 0);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder 대기열() {
        return get("/api/wholesale/orders/" + orderId + "/packings")
                .with(TestSecuritySupport.approvedAs(wholesalerId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder 취소(long packingId) {
        return delete("/api/wholesale/packings/" + packingId)
                .with(TestSecuritySupport.approvedAs(wholesalerId));
    }

    private int 정수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String 문자열(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
