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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포장 대기열 조회 API 통합 검증 (MUL-49).
 *
 * <p>대기열은 실체가 없다 — {@code status=READY AND outbound_id IS NULL} 이고 살아있는
 * 항목이 있는 포장이 전부다 (D-073). 바탕 픽스처: 가나상회(니트 3 + AGENT 셔츠 4) ·
 * 다라상회(니트 5). 각 테스트가 제외 대상 행을 스스로 심어 집계가 안 흔들리는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PackingQueueIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long leafId;
    private long colorId;
    private long 가나상회;
    private long 다라상회;
    private long 니트;
    private long 셔츠;
    private long batchId;
    private long 주문A;
    private long 니트라인A;
    private long 셔츠라인B;

    @BeforeEach
    void 대기열을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "queue@ondo.test", "9500000035");
        leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9160);
        colorId = MasterDataFixture.색상을_넣는다(jdbc, 9260, 9261);
        니트 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "니트", 1);
        셔츠 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "셔츠", 2);
        가나상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 711L, "가나상회");
        다라상회 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 712L, "다라상회");
        batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);

        주문A = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 1, "RETAILER", OffsetDateTime.now());
        니트라인A = OrderFixture.라인을_넣는다(jdbc, 주문A, 니트, 5, 1000, 3, 0);
        long 포장A = OrderFixture.포장을_넣는다(jdbc, 주문A, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장A, 니트라인A, null, batchId, 3, false);

        long 주문B = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 2, "AGENT", OffsetDateTime.now());
        셔츠라인B = OrderFixture.라인을_넣는다(jdbc, 주문B, 셔츠, 4, 2000, 4, 0);
        long 포장B = OrderFixture.포장을_넣는다(jdbc, 주문B, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장B, 셔츠라인B, null, batchId, 4, false);

        long 주문C = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 다라상회, 3, "RETAILER", OffsetDateTime.now());
        long 니트라인C = OrderFixture.라인을_넣는다(jdbc, 주문C, 니트, 5, 1000, 5, 0);
        long 포장C = OrderFixture.포장을_넣는다(jdbc, 주문C, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장C, 니트라인C, null, batchId, 5, false);
    }

    @Test
    void 소매처목록은_페이징없이_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/packing-items/retailers")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].retailerId").value(711))
                .andExpect(jsonPath("$.data[0].retailerName").value("가나상회"))
                .andExpect(jsonPath("$.data[0].itemCount").value(2))
                .andExpect(jsonPath("$.data[0].totalQty").value(7))
                .andExpect(jsonPath("$.data[1].retailerName").value("다라상회"))
                .andExpect(jsonPath("$.data[1].itemCount").value(1))
                .andExpect(jsonPath("$.data[1].totalQty").value(5));
    }

    @Test
    void 거래이력없는_retailerId는_404다() throws Exception {
        mvc.perform(get("/api/wholesale/packing-items").param("retailerId", "99999")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 대기열은_READY이고_출고에_안잡힌_포장만_센다() throws Exception {
        // 이미 출고에 묶인 PACKED 포장은 대기열이 아니다
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 가나상회, 1);
        long 묶인포장 = OutboundFixture.묶인_포장을_넣는다(jdbc, 주문A, outboundId);
        long 묶인항목 = OrderFixture.포장항목을_넣는다(jdbc, 묶인포장, 니트라인A, null, batchId, 2, false);

        mvc.perform(get("/api/wholesale/packing-items/retailers")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemCount").value(2))
                .andExpect(jsonPath("$.data[0].totalQty").value(7));
        mvc.perform(get("/api/wholesale/packing-items").param("retailerId", "711")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(묶인항목)).doesNotExist());
    }

    @Test
    void 배분취소된_항목은_집계에서_빠진다() throws Exception {
        long 취소된항목 = OrderFixture.포장항목을_넣는다(
                jdbc, OrderFixture.포장을_넣는다(jdbc, 주문A, "READY"), 니트라인A, null, batchId, 2, true);

        mvc.perform(get("/api/wholesale/packing-items/retailers")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemCount").value(2))
                .andExpect(jsonPath("$.data[0].totalQty").value(7));
        mvc.perform(get("/api/wholesale/packing-items").param("retailerId", "711")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(취소된항목)).doesNotExist());
    }

    @Test
    void receiveBy필터가_헤더와_펼침에_같이_걸린다() throws Exception {
        mvc.perform(get("/api/wholesale/packing-items/retailers").param("receiveBy", "AGENT")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].retailerId").value(711))
                .andExpect(jsonPath("$.data[0].itemCount").value(1))
                .andExpect(jsonPath("$.data[0].totalQty").value(4));
        mvc.perform(get("/api/wholesale/packing-items")
                        .param("retailerId", "711").param("receiveBy", "AGENT")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("셔츠"))
                .andExpect(jsonPath("$.data[0].receiveBy").value("AGENT"))
                .andExpect(jsonPath("$.data[0].qty").value(4));
    }

    @Test
    void q는_상품명만_거른다() throws Exception {
        // 소매처명은 검색 대상이 아니다(외부 시스템) — 상호로 걸면 빈 결과
        mvc.perform(get("/api/wholesale/packing-items/retailers").param("q", "가나상회")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/wholesale/packing-items/retailers").param("q", "셔츠")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].retailerId").value(711))
                .andExpect(jsonPath("$.data[0].itemCount").value(1));
        mvc.perform(get("/api/wholesale/packing-items")
                        .param("retailerId", "711").param("q", "셔츠")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("셔츠"));
    }

    @Test
    void 펼침은_2XL_라벨_사이즈도_그대로_내린다() throws Exception {
        // DB 는 '2XL' 라벨로 저장한다 (SizeConverter) — enum 상수명(X2L)으로 읽으면 깨진다
        long 패딩 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "패딩", 3);
        jdbc.update("update wholesale.variant set size = '2XL' where id = ?", 패딩);
        long 주문E = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 가나상회, 4, "RETAILER", OffsetDateTime.now());
        long 라인E = OrderFixture.라인을_넣는다(jdbc, 주문E, 패딩, 2, 1000, 2, 0);
        long 포장E = OrderFixture.포장을_넣는다(jdbc, 주문E, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 포장E, 라인E, null, batchId, 2, false);

        mvc.perform(get("/api/wholesale/packing-items")
                        .param("retailerId", "711").param("q", "패딩")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].size").value("2XL"));
    }

    @Test
    void 필터에_안걸리면_빈배열이다() throws Exception {
        mvc.perform(get("/api/wholesale/packing-items")
                        .param("retailerId", "711").param("q", "패딩")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
