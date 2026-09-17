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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 선수금으로 정산 · 선수금 요약 (MUL-125).
 *
 * <p>바탕 — 봄봄이 미송 바지 값을 먼저 냈다. 16일 전 10만, 12일 전 30만(둘 다 배분 없이 선수금).
 * 그 뒤 바지 주문 40만원이 출고됐다. 새 입금 없이 선수금으로 바지를 정산한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllocationCreateIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceivableLedgerWriter ledgerWriter;

    private long wholesalerId;
    private long 봄봄;
    private long 바지주문;
    private long 니트주문;
    private long 첫입금;
    private long 둘째입금;

    @BeforeEach
    void 선수금과_출고된_주문을_심는다() throws Exception {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "allocation-create@ondo.test", "9500000127");
        봄봄 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 201L, "봄봄상회");
        OffsetDateTime now = OffsetDateTime.now();
        첫입금 = 입금한다(100000, now.minusDays(16));
        둘째입금 = 입금한다(300000, now.minusDays(12));

        바지주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, 1, "AGENT", now.minusDays(12));
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 1);
        ledgerWriter.append(봄봄, now, List.of(ReceivableLedgerWriter.Line.outbound(바지주문, outboundId, 400000)));
        니트주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, 2, "AGENT", now);
    }

    @Test
    void 선수금으로_출고된_주문을_정산하고_원장은_그대로다() throws Exception {
        int 원장줄 = 수("select count(*) from wholesale.receivable_ledger where partner_id = " + 봄봄);
        int 미수 = 수("select receivable_balance from wholesale.partner where id = " + 봄봄);

        정산한다(UUID.randomUUID().toString(), "[ {\"orderId\": %d, \"amount\": 400000} ]".formatted(바지주문))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.retailerId").value(201))
                .andExpect(jsonPath("$.data.prepaidRemaining").value(0))
                // 오래된 입금부터 — 첫 입금 10만을 다 쓰고 둘째 입금에서 30만
                .andExpect(jsonPath("$.data.allocations.length()").value(2))
                .andExpect(jsonPath("$.data.allocations[0].paymentId").value(첫입금))
                .andExpect(jsonPath("$.data.allocations[0].amount").value(100000))
                .andExpect(jsonPath("$.data.allocations[1].paymentId").value(둘째입금))
                .andExpect(jsonPath("$.data.allocations[1].amount").value(300000))
                .andExpect(jsonPath("$.data.allocations[1].orderNumber").value(1));

        assertThat(수("select count(*) from wholesale.receivable_ledger where partner_id = " + 봄봄)).isEqualTo(원장줄);
        assertThat(수("select receivable_balance from wholesale.partner where id = " + 봄봄)).isEqualTo(미수);
    }

    @Test
    void 일부만_정산하면_남은_선수금은_둘째_입금에_남는다() throws Exception {
        정산한다(UUID.randomUUID().toString(), "[ {\"orderId\": %d, \"amount\": 150000} ]".formatted(바지주문))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.prepaidRemaining").value(250000));

        mvc.perform(get("/api/wholesale/receivables/retailers/201/prepaid")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retailerId").value(201))
                .andExpect(jsonPath("$.data.totalPaid").value(400000))
                .andExpect(jsonPath("$.data.totalAllocated").value(150000))
                .andExpect(jsonPath("$.data.prepaid").value(250000));
    }

    @Test
    void 선수금보다_많이_붙이면_409다() throws Exception {
        // 코트 미수 50만은 넉넉하지만 선수금이 40만뿐이다
        long 코트주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, 3, "AGENT", OffsetDateTime.now());
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 3);
        ledgerWriter.append(봄봄, OffsetDateTime.now(),
                List.of(ReceivableLedgerWriter.Line.outbound(코트주문, outboundId, 500000)));

        정산한다(UUID.randomUUID().toString(), "[ {\"orderId\": %d, \"amount\": 400001} ]".formatted(코트주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_PREPAID"));
        assertThat(수("select count(*) from wholesale.payment_allocation where order_id = " + 코트주문)).isZero();
    }

    @Test
    void 출고_전_주문에는_선수금도_붙일_수_없다() throws Exception {
        정산한다(UUID.randomUUID().toString(), "[ {\"orderId\": %d, \"amount\": 10000} ]".formatted(니트주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_OUTSTANDING"));
    }

    @Test
    void 같은_키에_같은_본문은_200으로_첫_응답을_내리고_배분은_한_번이다() throws Exception {
        String key = UUID.randomUUID().toString();
        String lines = "[ {\"orderId\": %d, \"amount\": 50000} ]".formatted(바지주문);
        String first = 정산한다(key, lines).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = 정산한다(key, lines).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(수("select count(*) from wholesale.payment_allocation where order_id = " + 바지주문)).isEqualTo(1);
    }

    @Test
    void 붙일_주문이_없으면_400이다() throws Exception {
        정산한다(UUID.randomUUID().toString(), "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 거래_관계가_없는_소매처의_선수금_요약은_404다() throws Exception {
        mvc.perform(get("/api/wholesale/receivables/retailers/999/prepaid")
                        .with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private long 입금한다(long amount, OffsetDateTime paidAt) throws Exception {
        String body = mvc.perform(post("/api/wholesale/payments")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "retailerId": 201, "amount": %d, "paidAt": "%s",
                                  "paidBy": "RETAILER", "method": "BANK_TRANSFER", "allocations": [] }
                                """.formatted(amount, paidAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions 정산한다(String key, String lines) throws Exception {
        return mvc.perform(post("/api/wholesale/allocations")
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"retailerId\": 201, \"allocations\": %s }".formatted(lines)));
    }

    private Integer 수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
