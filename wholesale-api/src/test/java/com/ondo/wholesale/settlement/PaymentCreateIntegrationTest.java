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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입금 등록 (MUL-124) — 입금만 · 입금 및 정산 · 멱등 · 배분 상한 · 계약 에러.
 *
 * <p>바탕 — 무드온(도매) ↔ 봄봄(소매 101). 셔츠 주문 3만원 출고, 바지 주문 10만원 출고,
 * 니트 주문은 확정만 되고 출고 전. 미수는 출고 확정과 같은 원장 쓰기로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentCreateIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceivableLedgerWriter ledgerWriter;

    private long wholesalerId;
    private long 봄봄;
    private long 셔츠주문;
    private long 바지주문;
    private long 니트주문;
    /** 멱등 비교가 본문 지문이라 입금 시각은 한 번만 정한다 — 부를 때마다 now() 면 같은 요청이 아니게 된다. */
    private OffsetDateTime 입금시각;

    @BeforeEach
    void 출고된_주문을_심는다() {
        입금시각 = OffsetDateTime.now().minusHours(1);
        wholesalerId = MasterDataFixture.도매처를_넣는다(jdbc, "payment-create@ondo.test", "9500000125");
        봄봄 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 101L, "봄봄상회");
        셔츠주문 = 출고된_주문(1, 30000);
        바지주문 = 출고된_주문(2, 100000);
        니트주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, 3, "RETAILER", OffsetDateTime.now());
    }

    @Test
    void 입금만_하면_선수금으로_남고_원장과_거래처_미수가_준다() throws Exception {
        입금한다(키(), 150000, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.retailerId").value(101))
                .andExpect(jsonPath("$.data.retailerName").value("봄봄상회"))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.unallocatedAmount").value(150000))
                .andExpect(jsonPath("$.data.prepaidRemaining").value(150000))
                .andExpect(jsonPath("$.data.allocations.length()").value(0))
                // 출고 13만 − 입금 15만 = 2만 선수금. 화면 계약은 양수 = 선수금
                .andExpect(jsonPath("$.data.ledgerBalance").value(20000));

        assertThat(수("select count(*) from wholesale.payment where partner_id = " + 봄봄)).isEqualTo(1);
        assertThat(수("select delta from wholesale.receivable_ledger where entry_type = 'PAYMENT' and partner_id = " + 봄봄))
                .isEqualTo(-150000);
        assertThat(수("select count(*) from wholesale.receivable_ledger where entry_type = 'PAYMENT' and order_id is null"))
                .isEqualTo(1);
        assertThat(수("select receivable_balance from wholesale.partner where id = " + 봄봄)).isEqualTo(-20000);
        assertThat(수("select count(*) from wholesale.payment_allocation")).isZero();
    }

    @Test
    void 입금_및_정산이면_배분이_남고_원장은_입금_한_줄이다() throws Exception {
        입금한다(키(), 130000, """
                [ {"orderId": %d, "amount": 30000}, {"orderId": %d, "amount": 100000} ]
                """.formatted(셔츠주문, 바지주문))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unallocatedAmount").value(0))
                .andExpect(jsonPath("$.data.allocations.length()").value(2))
                .andExpect(jsonPath("$.data.allocations[0].orderId").value(셔츠주문))
                .andExpect(jsonPath("$.data.allocations[0].orderNumber").value(1))
                .andExpect(jsonPath("$.data.allocations[0].amount").value(30000))
                .andExpect(jsonPath("$.data.allocations[1].orderNumber").value(2))
                .andExpect(jsonPath("$.data.ledgerBalance").value(0));

        // 배분은 돈이 오간 게 아니라 이름표 — 원장에는 입금 한 줄뿐이다
        assertThat(수("select count(*) from wholesale.receivable_ledger where entry_type = 'PAYMENT' and partner_id = " + 봄봄))
                .isEqualTo(1);
        assertThat(수("select sum(amount) from wholesale.payment_allocation where order_id = " + 바지주문))
                .isEqualTo(100000);
    }

    @Test
    void 같은_키에_같은_본문은_200으로_첫_응답을_내리고_입금은_하나다() throws Exception {
        String key = 키();
        String allocations = "[ {\"orderId\": %d, \"amount\": 30000} ]".formatted(셔츠주문);
        String first = 입금한다(key, 50000, allocations)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = 입금한다(key, 50000, allocations)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(수("select count(*) from wholesale.payment where partner_id = " + 봄봄)).isEqualTo(1);
        assertThat(수("select count(*) from wholesale.payment_allocation")).isEqualTo(1);
    }

    @Test
    void 같은_키에_다른_본문은_409다() throws Exception {
        String key = 키();
        입금한다(key, 50000, "[]").andExpect(status().isCreated());

        입금한다(key, 60000, "[]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void 출고_전_주문에는_배분할_수_없다() throws Exception {
        입금한다(키(), 50000, "[ {\"orderId\": %d, \"amount\": 10000} ]".formatted(니트주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_OUTSTANDING"));
        assertThat(수("select count(*) from wholesale.payment")).isZero();
    }

    @Test
    void 이미_붙은_배분을_빼고_남은_미수까지만_붙인다() throws Exception {
        입금한다(키(), 20000, "[ {\"orderId\": %d, \"amount\": 20000} ]".formatted(셔츠주문))
                .andExpect(status().isCreated());

        // 셔츠 3만 중 2만이 이미 붙어 1만만 남았다
        입금한다(키(), 20000, "[ {\"orderId\": %d, \"amount\": 20000} ]".formatted(셔츠주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_OUTSTANDING"));
        입금한다(키(), 10000, "[ {\"orderId\": %d, \"amount\": 10000} ]".formatted(셔츠주문))
                .andExpect(status().isCreated());
    }

    @Test
    void 배분_합계가_입금액을_넘으면_409다() throws Exception {
        입금한다(키(), 50000, """
                [ {"orderId": %d, "amount": 30000}, {"orderId": %d, "amount": 30000} ]
                """.formatted(셔츠주문, 바지주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_PAYMENT"));
    }

    @Test
    void 이번_입금이_모자라면_남은_선수금을_오래된_입금부터_끌어_쓴다() throws Exception {
        입금한다(키(), 20000, 입금시각.minusDays(2), 101, "[]").andExpect(status().isCreated());
        입금한다(키(), 20000, 입금시각.minusDays(1), 101, "[]").andExpect(status().isCreated());
        long 첫입금 = 가장_오래된_입금_id();

        // 바지 10만 = 이번 입금 7만 + 첫 입금 2만 + 둘째 입금 1만
        입금한다(키(), 70000, "[ {\"orderId\": %d, \"amount\": 100000} ]".formatted(바지주문))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unallocatedAmount").value(0))
                .andExpect(jsonPath("$.data.allocations.length()").value(3))
                .andExpect(jsonPath("$.data.allocations[0].amount").value(70000))
                .andExpect(jsonPath("$.data.allocations[1].paymentId").value(첫입금))
                .andExpect(jsonPath("$.data.allocations[1].amount").value(20000))
                .andExpect(jsonPath("$.data.allocations[2].amount").value(10000))
                .andExpect(jsonPath("$.data.prepaidRemaining").value(10000));
    }

    @Test
    void 이번_입금과_선수금을_합쳐도_모자라면_409다() throws Exception {
        입금한다(키(), 20000, 입금시각.minusDays(1), 101, "[]").andExpect(status().isCreated());

        입금한다(키(), 70000, "[ {\"orderId\": %d, \"amount\": 100000} ]".formatted(바지주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_PAYMENT"));
    }

    @Test
    void 확정_안_된_주문에는_배분할_수_없다() throws Exception {
        long 신규주문 = OrderFixture.주문을_넣는다(jdbc, wholesalerId, 봄봄, 9, "NEW", OffsetDateTime.now());

        입금한다(키(), 50000, "[ {\"orderId\": %d, \"amount\": 10000} ]".formatted(신규주문))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CONFIRMED"));
    }

    @Test
    void 다른_소매처_주문에는_배분할_수_없다() throws Exception {
        long 라라 = OrderFixture.거래처를_넣는다(jdbc, wholesalerId, 102L, "라라상회");
        long 라라주문 = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 라라, 10, "RETAILER", OffsetDateTime.now());

        입금한다(키(), 50000, "[ {\"orderId\": %d, \"amount\": 10000} ]".formatted(라라주문))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_RETAILER_MISMATCH"));
    }

    @Test
    void 남의_도매처_주문은_404다() throws Exception {
        long 남의도매 = MasterDataFixture.도매처를_넣는다(jdbc, "payment-other@ondo.test", "9500000126");
        long 남의거래처 = OrderFixture.거래처를_넣는다(jdbc, 남의도매, 101L, "봄봄상회");
        long 남의주문 = OutboundFixture.확정주문을_넣는다(jdbc, 남의도매, 남의거래처, 1, "RETAILER", OffsetDateTime.now());

        입금한다(키(), 50000, "[ {\"orderId\": %d, \"amount\": 10000} ]".formatted(남의주문))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 같은_주문을_두_번_적으면_400이다() throws Exception {
        입금한다(키(), 50000, """
                [ {"orderId": %d, "amount": 10000}, {"orderId": %d, "amount": 10000} ]
                """.formatted(셔츠주문, 셔츠주문))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ORDER"));
    }

    @Test
    void 미래_입금_시각은_400이다() throws Exception {
        입금한다(키(), 50000, OffsetDateTime.now().plusDays(1), 101, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAID_AT_IN_FUTURE"));
    }

    @Test
    void 거래_관계가_없는_소매처는_404다() throws Exception {
        입금한다(키(), 50000, OffsetDateTime.now(), 999, "[]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 금액이_0이면_400이다() throws Exception {
        입금한다(키(), 0, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private long 출고된_주문(int orderNumber, long amount) {
        long orderId = OutboundFixture.확정주문을_넣는다(jdbc, wholesalerId, 봄봄, orderNumber, "RETAILER",
                OffsetDateTime.now());
        long outboundId = OutboundFixture.출고를_넣는다(jdbc, wholesalerId, 봄봄, orderNumber);
        ledgerWriter.append(봄봄, OffsetDateTime.now(),
                List.of(ReceivableLedgerWriter.Line.outbound(orderId, outboundId, amount)));
        return orderId;
    }

    private ResultActions 입금한다(String key, long amount, String allocations) throws Exception {
        return 입금한다(key, amount, 입금시각, 101, allocations);
    }

    private ResultActions 입금한다(String key, long amount, OffsetDateTime paidAt, long retailerId,
                               String allocations) throws Exception {
        return mvc.perform(post("/api/wholesale/payments")
                .with(TestSecuritySupport.approvedAs(wholesalerId))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "retailerId": %d, "amount": %d, "paidAt": "%s",
                          "paidBy": "AGENT", "method": "CASH", "memo": "삼촌 대납",
                          "allocations": %s }
                        """.formatted(retailerId, amount, paidAt, allocations)));
    }

    private long 가장_오래된_입금_id() {
        return jdbc.queryForObject(
                "select id from wholesale.payment where partner_id = ? order by paid_at, id limit 1", Long.class, 봄봄);
    }

    private static String 키() {
        return UUID.randomUUID().toString();
    }

    private Integer 수(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
