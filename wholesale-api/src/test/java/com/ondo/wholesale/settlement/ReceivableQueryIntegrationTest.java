package com.ondo.wholesale.settlement;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.settlement.service.ReceivableLedgerWriter;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 탭 조회 (MUL-126) — 거래처별 미수 · 미수원장 · 주문 정산 상태가 실제 돈 흐름을 따라가는지.
 *
 * <p>바탕 — 봄봄(301): 바지 주문 40만원 두 시간 전 출고, 한 시간 전 10만원 입금(배분 없음 → 선수금).
 * 라라(302): 주문 없이 5만원 선수금만. 가나(303): 거래처만 있고 아무 거래 없음.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceivableQueryIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceivableLedgerWriter ledgerWriter;

    private long wholesalerId;
    private long 바지주문;
    private long 봄봄입금;

    @BeforeEach
    void 거래를_심는다() throws Exception {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "receivable-query@ondo.test", "9500000128");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9360);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9460, 9461);
        long 바지 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "바지", 1);

        long 봄봄 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 301L, "봄봄상회");
        OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 302L, "라라상회");
        OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 303L, "가나상회");

        OffsetDateTime now = OffsetDateTime.now();
        바지주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, 7, "AGENT", now.minusDays(1));
        OrderFixture.라인을_넣는다(jdbc, 바지주문, 바지, 20, 20000, 20, 20);
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 1);
        ledgerWriter.append(봄봄, now.minusHours(2),
                List.of(ReceivableLedgerWriter.Line.outbound(바지주문, outboundId, 400000)));

        봄봄입금 = 입금한다(301, 100000, now.minusHours(1));
        입금한다(302, 50000, now.minusHours(1));
    }

    @Test
    void 선수금으로_정산하면_주문이_정산_완료로_보인다() throws Exception {
        입금한다(301, 300000, OffsetDateTime.now().minusMinutes(30));

        mvc.perform(get("/api/wholesale/orders?retailerId=301").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(바지주문))
                .andExpect(jsonPath("$.data[0].shippedAmount").value(400000))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(400000))
                .andExpect(jsonPath("$.data[0].settlementStatus").value("UNPAID"));

        mvc.perform(post("/api/wholesale/allocations")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"retailerId\": 301, \"allocations\": [ {\"orderId\": %d, \"amount\": 150000} ] }"
                                .formatted(바지주문)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/wholesale/orders?retailerId=301").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(250000))
                .andExpect(jsonPath("$.data[0].settlementStatus").value("PARTIALLY_SETTLED"));

        mvc.perform(post("/api/wholesale/allocations")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"retailerId\": 301, \"allocations\": [ {\"orderId\": %d, \"amount\": 250000} ] }"
                                .formatted(바지주문)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/wholesale/orders?retailerId=301&settlementStatus=SETTLED")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(0))
                .andExpect(jsonPath("$.data[0].settlementStatus").value("SETTLED"));
    }

    @Test
    void 거래처_목록은_빚이_큰_순이고_거래가_없는_거래처는_빠진다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables/retailers").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // 봄봄: 출고 40만 − 입금 10만 = 30만 채무 → 화면 음수
                .andExpect(jsonPath("$.data[0].retailerId").value(301))
                .andExpect(jsonPath("$.data[0].retailerName").value("봄봄상회"))
                .andExpect(jsonPath("$.data[0].orderCount").value(1))
                .andExpect(jsonPath("$.data[0].ledgerBalance").value(-300000))
                .andExpect(jsonPath("$.data[0].lastOccurredAt").isNotEmpty())
                // 라라: 주문 없이 선수금 5만 → 화면 양수
                .andExpect(jsonPath("$.data[1].retailerId").value(302))
                .andExpect(jsonPath("$.data[1].orderCount").value(0))
                .andExpect(jsonPath("$.data[1].ledgerBalance").value(50000))
                .andExpect(jsonPath("$.meta.totalElements").value(2));

        mvc.perform(get("/api/wholesale/receivables/retailers?sort=retailerName,desc")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].retailerName").value("봄봄상회"));
    }

    @Test
    void 원장은_화면_부호로_최신순이고_현재_잔액은_필터와_무관하다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables?retailerId=301").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].entryType").value("PAYMENT"))
                .andExpect(jsonPath("$.data[0].balanceChange").value(100000))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(-300000))
                .andExpect(jsonPath("$.data[0].paymentId").value(봄봄입금))
                .andExpect(jsonPath("$.data[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data[1].entryType").value("SALE"))
                .andExpect(jsonPath("$.data[1].balanceChange").value(-400000))
                .andExpect(jsonPath("$.data[1].orderId").value(바지주문))
                .andExpect(jsonPath("$.data[1].orderNumber").value(7))
                .andExpect(jsonPath("$.meta.ledgerBalance").value(-300000));

        mvc.perform(get("/api/wholesale/receivables?retailerId=301&entryType=SALE&sort=occurredAt,asc")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].entryType").value("SALE"))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.ledgerBalance").value(-300000));
    }

    @Test
    void 거래_이력이_없는_소매처의_원장은_빈_목록과_잔액_0이다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables?retailerId=303").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.ledgerBalance").value(0));
        mvc.perform(get("/api/wholesale/receivables?retailerId=999").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 요청이_틀리면_400이다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("retailerId"));
        mvc.perform(get("/api/wholesale/receivables?retailerId=301&entryType=NOPE")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entryType"));
        mvc.perform(get("/api/wholesale/receivables/retailers?sort=nope")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        mvc.perform(get("/api/wholesale/receivables/retailers?size=101")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    private long 입금한다(long retailerId, long amount, OffsetDateTime paidAt) throws Exception {
        String body = mvc.perform(post("/api/wholesale/payments")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "retailerId": %d, "amount": %d, "paidAt": "%s",
                                  "paidBy": "RETAILER", "method": "BANK_TRANSFER", "allocations": [] }
                                """.formatted(retailerId, amount, paidAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
