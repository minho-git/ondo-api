package com.ondo.wholesale.dashboard;

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
 * summary 조립 통합 검증 (MUL-120) — 실 DB 픽스처로 GET 한 번에 모든 영역이 채워지는지.
 * 각 집계의 술어 상세는 {@code DashboardSummaryReaderIntegrationTest}가 전담한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardSummaryIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long wholesalerId;
    private long 봄봄;
    private long variantId;
    private int nextOrderNumber = 1;

    @BeforeEach
    void 도매처와_마스터를_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "dashboard-summary@ondo.test", "9500000052");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9580);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9680, 9681);
        봄봄 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 761L, "봄봄");
        variantId = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "티셔츠", 1);
    }

    @Test
    void summary가_모든_영역을_실데이터로_채운다() throws Exception {
        long batchId = OrderFixture.배분_배치를_넣는다(jdbc, wholesalerId);

        // 신규 주문 1건 (2장 × 10,000 — 오늘 주문 금액에도 잡힌다)
        // 시각은 now — 분 단위로 밀면 낮 12시 직후 실행에서 경계 앞으로 넘어가 깨질 수 있다
        long 신규 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++,
                "NEW", OffsetDateTime.now());
        OrderFixture.라인을_넣는다(jdbc, 신규, variantId, 2, 10000, 0, 0);

        // 포장 대기 7장
        long 대기주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄,
                nextOrderNumber++, "AGENT", OffsetDateTime.now());
        long 대기라인 = OrderFixture.라인을_넣는다(jdbc, 대기주문, variantId, 7, 1000, 7, 0);
        long 대기포장 = OrderFixture.포장을_넣는다(jdbc, 대기주문, "READY");
        OrderFixture.포장항목을_넣는다(jdbc, 대기포장, 대기라인, null, batchId, 7, false);

        // 미출고 봉투 2 — 하나는 26시간 전 포장(어느 실행 시각에도 이전 영업일) = stale
        OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 1);
        long 묵은봉투 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 2);
        jdbc.update("update wholesale.outbound set created_at = now() - interval '26 hours' where id = ?",
                묵은봉투);

        // 오늘 출고 봉투 1 (4장)
        long 출고주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄,
                nextOrderNumber++, "RETAILER", OffsetDateTime.now());
        long 출고라인 = OrderFixture.라인을_넣는다(jdbc, 출고주문, variantId, 4, 1000, 4, 4);
        long 출고봉투 = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 3);
        long 출고포장 = OutboundFixture.묶인_포장을_넣는다(jdbc, 출고주문, 출고봉투);
        OrderFixture.포장항목을_넣는다(jdbc, 출고포장, 출고라인, null, batchId, 4, false);
        OutboundFixture.출고를_확정한다(jdbc, 출고봉투, 1, OffsetDateTime.now());

        // OPEN 미송 1 SKU (잔여 3, 입고일 미등록)
        long 미송주문 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now());
        long 미송라인 = OrderFixture.라인을_넣는다(jdbc, 미송주문, variantId, 5, 1000, 2, 0);
        OrderFixture.미송을_넣는다(jdbc, 미송라인, 3, "OPEN");

        // 이전 영업일 주문(26시간 전) — today 에 잡히면 안 된다
        long 지난주문 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, nextOrderNumber++,
                "CONFIRMED", OffsetDateTime.now().minusHours(26));
        OrderFixture.라인을_넣는다(jdbc, 지난주문, variantId, 9, 1000, 0, 0);

        mvc.perform(get("/api/wholesale/dashboard/summary")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.now").exists())
                .andExpect(jsonPath("$.data.newOrders.count").value(1))
                .andExpect(jsonPath("$.data.newOrders.oldestRetailerName").value("봄봄"))
                .andExpect(jsonPath("$.data.packing.retailerCount").value(1))
                .andExpect(jsonPath("$.data.packing.qty").value(7))
                .andExpect(jsonPath("$.data.packing.byReceive.AGENT").value(1))
                .andExpect(jsonPath("$.data.packing.byReceive.RETAILER").value(0))
                .andExpect(jsonPath("$.data.outbound.notShippedCount").value(2))
                .andExpect(jsonPath("$.data.outbound.staleCount").value(1))
                .andExpect(jsonPath("$.data.backorder.skuCount").value(1))
                .andExpect(jsonPath("$.data.backorder.qty").value(3))
                .andExpect(jsonPath("$.data.backorder.overdueSkuCount").value(0))
                .andExpect(jsonPath("$.data.backorder.noDateSkuCount").value(1))
                // 오늘 주문: 신규 + 포장·출고·미송 주문 4건 — 26시간 전 주문은 빠진다
                .andExpect(jsonPath("$.data.today.orders.count").value(4))
                .andExpect(jsonPath("$.data.today.orders.amount").value(36000))
                .andExpect(jsonPath("$.data.today.cancelled").value(0))
                .andExpect(jsonPath("$.data.today.shipped.count").value(1))
                .andExpect(jsonPath("$.data.today.shipped.qty").value(4));
    }
}
