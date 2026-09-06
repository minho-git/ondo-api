package com.ondo.wholesale.outbound;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.MasterDataFixture;
import com.ondo.wholesale.support.OrderFixture;
import com.ondo.wholesale.support.OutboundFixture;
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
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포장 완료(출고 생성) API 통합 검증 (MUL-49).
 *
 * <p>체크한 SKU 행들이 한 봉투로 묶인다 — 재고는 아직 줄지 않는다(차감은 출고 확정).
 * 전체 선택은 포장 id 를 유지한 채 PACKED 전이, 일부 선택은 분할이다(남는 쪽이
 * 대기열 잔류, D-073). 바탕 픽스처: 가나상회 주문A(니트 3 + 셔츠 4)·주문B(니트 2),
 * 다라상회 주문C(니트 1), 가나상회 AGENT 주문D(니트 1) — 전부 READY 포장.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutboundCreateIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long 가나상회;
    private long 니트;
    private long 셔츠;
    private long batchId;
    private long 포장A;
    private long 항목A니트;
    private long 항목A셔츠;
    private long 포장B;
    private long 항목B니트;
    private long 항목C;
    private long 항목D;

    @BeforeEach
    void 대기열을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "outbound-create@ondo.test", "9500000036");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9160);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9260, 9261);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "셔츠", 2);
        jdbc.update("update wholesale.variant set stock_qty = 10, reserved_qty = 7 where id = ?", 니트);
        jdbc.update("update wholesale.variant set stock_qty = 8, reserved_qty = 4 where id = ?", 셔츠);
        가나상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 721L, "가나상회");
        long 다라상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 722L, "다라상회");
        batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);

        long 주문A = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 1, "RETAILER", OffsetDateTime.now());
        long 니트라인A = OrderFixture.라인을_넣는다(jdbc, 주문A, 니트, 5, 1000, 3, 0);
        long 셔츠라인A = OrderFixture.라인을_넣는다(jdbc, 주문A, 셔츠, 4, 2000, 4, 0);
        포장A = OrderFixture.포장을_넣는다(jdbc, 주문A, "READY");
        항목A니트 = OrderFixture.포장항목을_넣는다(jdbc, 포장A, 니트라인A, null, batchId, 3, false);
        항목A셔츠 = OrderFixture.포장항목을_넣는다(jdbc, 포장A, 셔츠라인A, null, batchId, 4, false);

        long 주문B = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 2, "RETAILER", OffsetDateTime.now());
        long 니트라인B = OrderFixture.라인을_넣는다(jdbc, 주문B, 니트, 2, 1000, 2, 0);
        포장B = OrderFixture.포장을_넣는다(jdbc, 주문B, "READY");
        항목B니트 = OrderFixture.포장항목을_넣는다(jdbc, 포장B, 니트라인B, null, batchId, 2, false);

        long 주문C = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 다라상회, 3, "RETAILER", OffsetDateTime.now());
        long 니트라인C = OrderFixture.라인을_넣는다(jdbc, 주문C, 니트, 1, 1000, 1, 0);
        long 포장C = OrderFixture.포장을_넣는다(jdbc, 주문C, "READY");
        항목C = OrderFixture.포장항목을_넣는다(jdbc, 포장C, 니트라인C, null, batchId, 1, false);

        long 주문D = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 4, "AGENT", OffsetDateTime.now());
        long 니트라인D = OrderFixture.라인을_넣는다(jdbc, 주문D, 니트, 1, 1000, 1, 0);
        long 포장D = OrderFixture.포장을_넣는다(jdbc, 주문D, "READY");
        항목D = OrderFixture.포장항목을_넣는다(jdbc, 포장D, 니트라인D, null, batchId, 1, false);
    }

    @Test
    void 전체선택은_포장id를_유지한채_PACKED로_묶는다() throws Exception {
        mvc.perform(포장완료(항목A니트, 항목A셔츠, 항목B니트))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outboundNumber").value(1))
                .andExpect(jsonPath("$.data.retailerId").value(721))
                .andExpect(jsonPath("$.data.retailerName").value("가나상회"))
                .andExpect(jsonPath("$.data.shippedAt").value((Object) null))
                .andExpect(jsonPath("$.data.statementNumber").value((Object) null))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.totalQty").value(9))
                .andExpect(jsonPath("$.data.packings.length()").value(2))
                .andExpect(jsonPath("$.data.packings[0].id").value(포장A))
                .andExpect(jsonPath("$.data.packings[0].status").value("PACKED"))
                .andExpect(jsonPath("$.data.packings[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.packings[1].id").value(포장B))
                .andExpect(jsonPath("$.data.packings[1].orderNumber").value(2));

        Long outboundId = jdbc.queryForObject("select id from wholesale.outbound", Long.class);
        assertThat(jdbc.queryForObject(
                "select status from wholesale.packing where id = " + 포장A, String.class)).isEqualTo("PACKED");
        assertThat(jdbc.queryForObject(
                "select outbound_id from wholesale.packing where id = " + 포장A, Long.class)).isEqualTo(outboundId);
        assertThat(jdbc.queryForObject(
                "select outbound_id from wholesale.packing where id = " + 포장B, Long.class)).isEqualTo(outboundId);
    }

    @Test
    void 일부선택은_포장을_분할하고_남는쪽이_대기열에_남는다() throws Exception {
        mvc.perform(포장완료(항목A니트))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalQty").value(3))
                .andExpect(jsonPath("$.data.packings.length()").value(1))
                .andExpect(jsonPath("$.data.packings[0].status").value("PACKED"))
                .andExpect(jsonPath("$.data.packings[0].items.length()").value(1))
                .andExpect(jsonPath("$.data.packings[0].items[0].id").value(항목A니트));

        // 남는 쪽(포장A)이 id 를 유지한 채 대기열에 남는다 — 셔츠 항목이 그대로다
        assertThat(jdbc.queryForObject(
                "select status from wholesale.packing where id = " + 포장A, String.class)).isEqualTo("READY");
        assertThat(jdbc.queryForObject(
                "select outbound_id from wholesale.packing where id = " + 포장A, Long.class)).isNull();
        assertThat(jdbc.queryForObject(
                "select packing_id from wholesale.packing_item where id = " + 항목A셔츠, Long.class)).isEqualTo(포장A);
        // 나가는 항목은 id 를 유지한 채 새 포장으로 재부모화된다
        Long departedPackingId = jdbc.queryForObject(
                "select packing_id from wholesale.packing_item where id = " + 항목A니트, Long.class);
        assertThat(departedPackingId).isNotEqualTo(포장A);
        assertThat(jdbc.queryForObject(
                "select status from wholesale.packing where id = " + departedPackingId, String.class))
                .isEqualTo("PACKED");
    }

    @Test
    void 빈목록은_400_INVARIANT_VIOLATED다() throws Exception {
        mvc.perform(포장완료())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATED"));
    }

    @Test
    void 중복항목은_400이다() throws Exception {
        mvc.perform(포장완료(항목A니트, 항목A니트))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PACKING_ITEM"));
    }

    @Test
    void 소매처혼합은_400이다() throws Exception {
        mvc.perform(포장완료(항목A니트, 항목C))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RETAILER_MIXED"));
    }

    @Test
    void 수령방식혼합은_400이다() throws Exception {
        mvc.perform(포장완료(항목A니트, 항목D))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIVE_BY_MIXED"));
    }

    @Test
    void 이미묶인_포장은_409_PACKING_NOT_READY다() throws Exception {
        mvc.perform(포장완료(항목B니트)).andExpect(status().isCreated());

        mvc.perform(포장완료(항목B니트))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PACKING_NOT_READY"));
    }

    @Test
    void 배분취소된_항목은_404다() throws Exception {
        jdbc.update("update wholesale.packing_item set deleted_at = now() where id = ?", 항목A니트);

        mvc.perform(포장완료(항목A니트))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 재고와_예약은_변하지_않는다() throws Exception {
        mvc.perform(포장완료(항목A니트, 항목A셔츠, 항목B니트)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "select stock_qty from wholesale.variant where id = " + 니트, Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "select reserved_qty from wholesale.variant where id = " + 니트, Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "select stock_qty from wholesale.variant where id = " + 셔츠, Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "select reserved_qty from wholesale.variant where id = " + 셔츠, Integer.class)).isEqualTo(4);
    }

    private MockHttpServletRequestBuilder 포장완료(long... packingItemIds) {
        String ids = Arrays.stream(packingItemIds).mapToObj(Long::toString)
                .collect(Collectors.joining(", "));
        return post("/api/wholesale/outbounds")
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"packingItemIds\": [%s]}".formatted(ids));
    }
}
