package com.ondo.retail.wholesale.settlement;

import com.ondo.retail.settlement.dto.LedgerLine;
import com.ondo.retail.settlement.dto.SettlementSummaryLine;
import com.ondo.retail.wholesale.WholesaleApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 도매 정산 API 어댑터 (MUL-129) — 미송 어댑터 테스트와 같은 방식. 주소 · 옮기기 · 실패를 본다. */
class WholesaleSettlementAdapterTest {

    private static final String BASE = "http://wholesale.test";

    private MockRestServiceServer 도매;
    private WholesaleSettlementAdapter adapter;

    @BeforeEach
    void 가짜_도매를_세운다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        도매 = MockRestServiceServer.bindTo(builder).build();
        WholesaleSettlementApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(WholesaleSettlementApi.class);
        adapter = new WholesaleSettlementAdapter(api);
    }

    @Test
    @DisplayName("요약은 소매처 id 를 실어 부르고 연체를 풀어 옮긴다")
    void 요약을_옮긴다() {
        도매.expect(requestTo(BASE + "/api/retail-gateway/settlements?retailerId=42"))
                .andRespond(withSuccess("""
                        { "data": [ { "wholesalerId": 101, "wholesalerName": "무드온", "balance": 50000,
                                      "overdue": { "amount": 30000, "count": 1, "maxDays": 2 },
                                      "lastPaidAt": "2026-09-15", "paidLast7Days": 80000,
                                      "bankName": "국민", "bankAccountNo": "123-45-678", "bankAccountHolder": "김무드" } ] }
                        """, MediaType.APPLICATION_JSON));

        List<SettlementSummaryLine> 요약 = adapter.summaries(42L);

        도매.verify();
        assertThat(요약).containsExactly(new SettlementSummaryLine(101L, "무드온", 50000, 30000, 1, 2,
                LocalDate.of(2026, 9, 15), 80000, "국민", "123-45-678", "김무드"));
    }

    @Test
    @DisplayName("원장은 도매처 id 까지 실어 부르고 retailOrderId 를 소매 주문서 id 로 옮긴다")
    void 원장을_옮긴다() {
        도매.expect(requestTo(BASE + "/api/retail-gateway/settlements/ledger?retailerId=42&wholesalerId=101"))
                .andRespond(withSuccess("""
                        { "data": [
                            { "id": 1, "kind": "SHIPMENT", "date": "2026-08-12", "delta": 900000,
                              "retailOrderId": 5001, "statementNumber": 4, "shippedAt": "2026-08-12T02:00:00Z" },
                            { "id": 2, "kind": "PAYMENT", "date": "2026-08-16", "delta": -520000, "method": "CASH",
                              "allocations": [ { "retailOrderId": 5001, "amount": 520000 } ], "unallocated": 0 } ] }
                        """, MediaType.APPLICATION_JSON));

        List<LedgerLine> 원장 = adapter.ledger(42L, 101L);

        도매.verify();
        assertThat(원장.get(0).orderId()).isEqualTo(5001L);
        assertThat(원장.get(0).statementNumber()).isEqualTo(4);
        assertThat(원장.get(0).allocations()).isNull();
        assertThat(원장.get(1).allocations()).containsExactly(new LedgerLine.Allocation(5001L, 520000));
        assertThat(원장.get(1).unallocated()).isZero();
    }

    @Test
    @DisplayName("도매가 실패하면 도매 장애로 묶어 던진다")
    void 도매_실패는_하나로_묶는다() {
        도매.expect(requestTo(BASE + "/api/retail-gateway/settlements?retailerId=42"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.summaries(42L)).isInstanceOf(WholesaleApiException.class);
    }
}
