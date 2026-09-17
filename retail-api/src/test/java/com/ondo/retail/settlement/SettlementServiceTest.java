package com.ondo.retail.settlement;

import com.ondo.retail.backorder.OrderNoQuery;
import com.ondo.retail.settlement.dto.LedgerEntryResponse;
import com.ondo.retail.settlement.dto.LedgerLine;
import com.ondo.retail.settlement.dto.PartnerSettlementResponse;
import com.ondo.retail.settlement.dto.SettlementSummaryLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 정산 응답 조립 (MUL-129). 숫자는 도매가 계산해 오니, 여기서는 <b>소매가 채우는 것</b>만 본다 —
 * 주문번호 · 장끼 번호 · 결제 수단 이름 · 계좌 유무 · 세션 소매처 id 전달.
 */
class SettlementServiceTest {

    private static final long 우리 = 42L;
    private static final long 무드온 = 101L;

    private final OrderNoQuery orderNoQuery = mock(OrderNoQuery.class);

    @Test
    @DisplayName("출고 줄에 주문번호와 장끼 번호가, 입금 줄의 배분마다 주문번호가 붙는다")
    void 원장에_주문번호와_장끼_번호를_채운다() {
        SettlementClient client = 원장만(List.of(
                new LedgerLine(1L, "SHIPMENT", LocalDate.of(2026, 8, 12), 900000, 5001L, 4,
                        OffsetDateTime.parse("2026-08-11T16:30:00Z"), null, null, null),
                new LedgerLine(2L, "PAYMENT", LocalDate.of(2026, 8, 16), -500000, null, null, null,
                        "BANK_TRANSFER",
                        List.of(new LedgerLine.Allocation(5001L, 300000), new LedgerLine.Allocation(5002L, 200000)),
                        0L)));
        given(orderNoQuery.byOrderIds(eq(우리), anyCollection())).willReturn(Map.of(
                5001L, "20260812-1152-0014", 5002L, "20260813-0901-0015"));

        List<LedgerEntryResponse> 원장 = new SettlementService(client, orderNoQuery).ledger(우리, 무드온);

        LedgerEntryResponse 출고 = 원장.get(0);
        assertThat(출고.kind()).isEqualTo("SHIPMENT");
        assertThat(출고.orderNo()).isEqualTo("20260812-1152-0014");
        // 16:30Z 는 KST 로 다음 날 01:30 — 장끼 날짜는 KST 기준이다
        assertThat(출고.statementNo()).isEqualTo("JG-20260812-004");
        assertThat(출고.method()).isNull();

        LedgerEntryResponse 입금 = 원장.get(1);
        assertThat(입금.method()).isEqualTo("TRANSFER");
        assertThat(입금.delta()).isEqualTo(-500000);
        assertThat(입금.allocations()).extracting(LedgerEntryResponse.Allocation::orderNo)
                .containsExactly("20260812-1152-0014", "20260813-0901-0015");
        assertThat(입금.unallocated()).isZero();
    }

    @Test
    @DisplayName("주문번호를 못 찾아도 줄이 사라지지 않는다 — 버리면 잔액이 안 맞는다")
    void 주문번호가_없어도_줄은_남는다() {
        SettlementClient client = 원장만(List.of(
                new LedgerLine(1L, "SHIPMENT", LocalDate.of(2026, 8, 12), 29000, 9999L, 1,
                        OffsetDateTime.parse("2026-08-12T02:00:00Z"), null, null, null)));
        given(orderNoQuery.byOrderIds(eq(우리), anyCollection())).willReturn(Map.of());

        List<LedgerEntryResponse> 원장 = new SettlementService(client, orderNoQuery).ledger(우리, 무드온);

        assertThat(원장).hasSize(1);
        assertThat(원장.getFirst().orderNo()).isNull();
        assertThat(원장.getFirst().delta()).isEqualTo(29000);
    }

    @Test
    @DisplayName("요약은 도매 숫자를 그대로 옮기고, 계좌가 없으면 계좌 칸이 null 이다")
    void 요약을_옮긴다() {
        Long[] 넘긴값 = new Long[1];
        SettlementClient client = new SettlementClient() {
            @Override
            public List<SettlementSummaryLine> summaries(Long retailerId) {
                넘긴값[0] = retailerId;
                return List.of(
                        new SettlementSummaryLine(무드온, "무드온", 50000, 30000, 1, 2,
                                LocalDate.of(2026, 9, 15), 80000, "국민", "123-45-678", "김무드"),
                        new SettlementSummaryLine(102L, "라라도매", -20000, 0, 0, 0, null, 0, null, null, null));
            }

            @Override
            public List<LedgerLine> ledger(Long retailerId, Long wholesalerId) {
                return List.of();
            }
        };

        List<PartnerSettlementResponse> 요약 = new SettlementService(client, orderNoQuery).partners(우리);

        assertThat(넘긴값[0]).isEqualTo(우리);
        assertThat(요약.get(0).overdue()).isEqualTo(new PartnerSettlementResponse.Overdue(30000, 1, 2));
        assertThat(요약.get(0).bank()).isEqualTo(new PartnerSettlementResponse.Bank("국민", "123-45-678", "김무드"));
        assertThat(요약.get(1).balance()).isEqualTo(-20000);
        assertThat(요약.get(1).bank()).isNull();
    }

    private static SettlementClient 원장만(List<LedgerLine> lines) {
        return new SettlementClient() {
            @Override
            public List<SettlementSummaryLine> summaries(Long retailerId) {
                return List.of();
            }

            @Override
            public List<LedgerLine> ledger(Long retailerId, Long wholesalerId) {
                return lines;
            }
        };
    }
}
