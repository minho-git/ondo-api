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
 * 입금 취소 · 배분 취소 (MUL-127).
 *
 * <p>바탕 — 봄봄(401): 바지 주문 40만원 두 시간 전 출고(주문번호 5). 입금은 테스트마다 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementCancelIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceivableLedgerWriter ledgerWriter;

    private long wholesalerId;
    private long 봄봄;
    private long 바지주문;

    @BeforeEach
    void 출고된_주문을_심는다() {
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "settlement-cancel@ondo.test", "9500000129");
        long leafId = MasterDataFixture.카테고리_리프를_넣는다(jdbc, 9560);
        long colorId = MasterDataFixture.색상을_넣는다(jdbc, 9660, 9661);
        long 바지 = OrderFixture.상품_변형을_넣는다(jdbc, wholesalerId, leafId, colorId, "바지", 1);

        봄봄 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 401L, "봄봄상회");
        바지주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, 5, "AGENT", OffsetDateTime.now().minusDays(1));
        OrderFixture.라인을_넣는다(jdbc, 바지주문, 바지, 20, 20000, 20, 20);
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, 1);
        ledgerWriter.append(봄봄, OffsetDateTime.now().minusHours(2),
                List.of(ReceivableLedgerWriter.Line.outbound(바지주문, outboundId, 400000)));
    }

    @Test
    void 입금을_취소하면_원장에_반대_줄이_쌓이고_미수와_선수금이_입금_전으로_돌아간다() throws Exception {
        long 입금 = 입금한다(100000, "[]");
        int 원장줄 = 수("select count(*) from wholesale.receivable_ledger where partner_id = " + 봄봄);

        입금_취소(입금, "금액을 잘못 넣음")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(입금))
                .andExpect(jsonPath("$.data.voidReason").value("금액을 잘못 넣음"))
                .andExpect(jsonPath("$.data.voidedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.ledgerBalance").value(-400000))
                .andExpect(jsonPath("$.data.prepaidRemaining").value(0));

        // 입금 줄은 그대로 두고 반대 줄 하나 — 지우지 않는다
        assertThat(수("select count(*) from wholesale.receivable_ledger where partner_id = " + 봄봄)).isEqualTo(원장줄 + 1);
        assertThat(수("select delta from wholesale.receivable_ledger where entry_type = 'PAYMENT_VOID' and payment_id = " + 입금))
                .isEqualTo(100000);
        assertThat(수("select receivable_balance from wholesale.partner where id = " + 봄봄)).isEqualTo(400000);

        mvc.perform(get("/api/wholesale/receivables?retailerId=401").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].entryType").value("PAYMENT_VOID"))
                .andExpect(jsonPath("$.data[0].balanceChange").value(-100000))
                .andExpect(jsonPath("$.data[0].paymentId").value(입금))
                .andExpect(jsonPath("$.meta.ledgerBalance").value(-400000));
        mvc.perform(get("/api/wholesale/receivables/retailers/401/prepaid").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data.totalPaid").value(0))
                .andExpect(jsonPath("$.data.prepaid").value(0));
    }

    @Test
    void 배분된_입금을_취소하면_주문이_다시_미결제가_된다() throws Exception {
        long 입금 = 입금한다(400000, "[ {\"orderId\": %d, \"amount\": 400000} ]".formatted(바지주문));
        주문_정산상태_는("SETTLED", 0);

        입금_취소(입금, "다른 거래처 입금이었음").andExpect(status().isOk());

        주문_정산상태_는("UNPAID", 400000);
    }

    @Test
    void 이미_취소한_입금을_다시_취소하면_409다() throws Exception {
        long 입금 = 입금한다(100000, "[]");
        입금_취소(입금, "중복 등록").andExpect(status().isOk());

        입금_취소(입금, "중복 등록")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        assertThat(수("select count(*) from wholesale.receivable_ledger where entry_type = 'PAYMENT_VOID' and payment_id = " + 입금))
                .isEqualTo(1);
    }

    @Test
    void 사유가_없으면_400이다() throws Exception {
        long 입금 = 입금한다(100000, "[]");

        입금_취소(입금, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void 남의_도매처_입금은_404다() throws Exception {
        long 입금 = 입금한다(100000, "[]");
        long 남의도매 = MasterDataFixture.도매처를_넣는다(jdbc, "settlement-cancel-other@ondo.test", "9500000130");

        mvc.perform(post("/api/wholesale/payments/" + 입금 + "/void")
                        .with(TestSecuritySupport.approvedAs(남의도매))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"reason\": \"남의 것\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 배분을_취소하면_선수금으로_돌아가고_원장은_그대로다() throws Exception {
        입금한다(400000, "[ {\"orderId\": %d, \"amount\": 400000} ]".formatted(바지주문));
        long 배분 = 배분_id();
        int 원장줄 = 수("select count(*) from wholesale.receivable_ledger where partner_id = " + 봄봄);

        배분_취소(배분)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocationId").value(배분))
                .andExpect(jsonPath("$.data.orderId").value(바지주문))
                .andExpect(jsonPath("$.data.amount").value(400000))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.prepaidRemaining").value(400000));

        assertThat(수("select count(*) from wholesale.receivable_ledger where partner_id = " + 봄봄)).isEqualTo(원장줄);
        assertThat(수("select count(*) from wholesale.payment_allocation where id = " + 배분)).isEqualTo(1);
        주문_정산상태_는("UNPAID", 400000);

        // 떼어낸 돈은 선수금으로 다시 붙일 수 있다
        mvc.perform(post("/api/wholesale/allocations")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"retailerId\": 401, \"allocations\": [ {\"orderId\": %d, \"amount\": 400000} ] }"
                                .formatted(바지주문)))
                .andExpect(status().isCreated());
        주문_정산상태_는("SETTLED", 0);
    }

    @Test
    void 이미_취소한_배분과_취소된_입금의_배분은_409다() throws Exception {
        long 입금 = 입금한다(400000, "[ {\"orderId\": %d, \"amount\": 400000} ]".formatted(바지주문));
        long 배분 = 배분_id();
        배분_취소(배분).andExpect(status().isOk());
        배분_취소(배분)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        long 둘째입금 = 입금한다(100000, "[ {\"orderId\": %d, \"amount\": 100000} ]".formatted(바지주문));
        long 둘째배분 = jdbc.queryForObject(
                "select id from wholesale.payment_allocation where payment_id = ?", Long.class, 둘째입금);
        입금_취소(둘째입금, "잘못 넣음").andExpect(status().isOk());
        배분_취소(둘째배분)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        assertThat(입금).isNotEqualTo(둘째입금);
    }

    private void 주문_정산상태_는(String status, long outstanding) throws Exception {
        mvc.perform(get("/api/wholesale/orders?retailerId=401").with(TestSecuritySupport.approvedAs(wholesalerId)))
                .andExpect(jsonPath("$.data[0].settlementStatus").value(status))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(outstanding));
    }

    private long 입금한다(long amount, String allocations) throws Exception {
        String body = mvc.perform(post("/api/wholesale/payments")
                        .with(TestSecuritySupport.approvedAs(wholesalerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "retailerId": 401, "amount": %d, "paidAt": "%s",
                                  "paidBy": "AGENT", "method": "CASH", "allocations": %s }
                                """.formatted(amount, OffsetDateTime.now().minusHours(1), allocations)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceFirst("^\\{\"data\":\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions 입금_취소(long paymentId, String reason) throws Exception {
        return mvc.perform(post("/api/wholesale/payments/" + paymentId + "/void")
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"reason\": \"%s\" }".formatted(reason)));
    }

    private ResultActions 배분_취소(long allocationId) throws Exception {
        return mvc.perform(post("/api/wholesale/allocations/" + allocationId + "/cancel")
                .with(TestSecuritySupport.approvedAs(wholesalerId)));
    }

    private long 배분_id() {
        return jdbc.queryForObject("select id from wholesale.payment_allocation where order_id = ? order by id limit 1",
                Long.class, 바지주문);
    }

    private Integer 수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
