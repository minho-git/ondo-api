package com.ondo.wholesale.settlement;

import com.ondo.wholesale.order.PaymentMethod;
import com.ondo.wholesale.settlement.dto.LedgerEntryResponse;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import com.ondo.wholesale.settlement.dto.ReceivableLedgerResponse;
import com.ondo.wholesale.settlement.dto.ReceivableRetailerResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 계약 스텁 example — api-lite/07_정산 문서의 Response 예시 그대로. 실구현이 서비스 호출로 교체한다. */
final class SettlementStubExamples {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private SettlementStubExamples() {
    }

    static PaymentCreatedResponse createdPayment() {
        return new PaymentCreatedResponse(
                4401L, 3307L, "부산 상사", 400000,
                OffsetDateTime.of(2025, 8, 14, 15, 30, 0, 0, KST),
                PaidBy.RETAILER, PaymentMethod.CASH, "8월 정산금 납부",
                0,
                List.of(new PaymentCreatedResponse.Allocation(
                        7701L, 5606L, 6, 71000,
                        OffsetDateTime.of(2025, 8, 14, 15, 30, 12, 0, KST))),
                -235000,
                OffsetDateTime.of(2025, 8, 14, 15, 30, 12, 0, KST));
    }

    static List<ReceivableRetailerResponse> receivableRetailers() {
        return List.of(
                new ReceivableRetailerResponse(3301L, "RT-001", "서울유통", 6, -589000,
                        OffsetDateTime.of(2025, 8, 12, 11, 42, 0, 0, KST)),
                new ReceivableRetailerResponse(3307L, "RT-007", "부산 상사", 4, -235000,
                        OffsetDateTime.of(2025, 8, 12, 11, 42, 0, 0, KST)));
    }

    static ReceivableLedgerResponse ledger() {
        List<LedgerEntryResponse> entries = List.of(
                new LedgerEntryResponse(10203L, LedgerEntryType.SALE, -85000, -235000,
                        OffsetDateTime.of(2025, 8, 12, 11, 42, 0, 0, KST), 5603L, 3, null),
                new LedgerEntryResponse(10202L, LedgerEntryType.SALE, -350000, -150000,
                        OffsetDateTime.of(2025, 8, 11, 9, 15, 0, 0, KST), 5602L, 2, null),
                new LedgerEntryResponse(10201L, LedgerEntryType.PAYMENT, 200000, 200000,
                        OffsetDateTime.of(2025, 8, 10, 14, 30, 0, 0, KST), null, null, 4398L));
        return new ReceivableLedgerResponse(entries,
                new ReceivableLedgerResponse.LedgerMeta(0, 20, 3, 1, -235000));
    }
}
