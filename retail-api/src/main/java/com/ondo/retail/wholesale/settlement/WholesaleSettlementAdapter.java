package com.ondo.retail.wholesale.settlement;

import com.ondo.retail.settlement.SettlementClient;
import com.ondo.retail.settlement.dto.LedgerLine;
import com.ondo.retail.settlement.dto.SettlementSummaryLine;
import com.ondo.retail.wholesale.settlement.dto.WholesaleSettlementLedgerEntry;
import com.ondo.retail.wholesale.settlement.dto.WholesaleSettlementSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ondo.retail.wholesale.WholesaleCall.call;

/** 도매 정산 API 를 소매 말로 옮긴다 (MUL-129). 주문번호 · 장끼 번호는 {@code SettlementService} 가 채운다. */
@Component
@RequiredArgsConstructor
public class WholesaleSettlementAdapter implements SettlementClient {

    private final WholesaleSettlementApi api;

    @Override
    public List<SettlementSummaryLine> summaries(Long retailerId) {
        List<WholesaleSettlementSummary> data = call("정산 요약", () -> api.summaries(retailerId)).data();
        return data == null ? List.of() : data.stream().map(WholesaleSettlementAdapter::toLine).toList();
    }

    @Override
    public List<LedgerLine> ledger(Long retailerId, Long wholesalerId) {
        List<WholesaleSettlementLedgerEntry> data =
                call("정산 원장", () -> api.ledger(retailerId, wholesalerId)).data();
        return data == null ? List.of() : data.stream().map(WholesaleSettlementAdapter::toLine).toList();
    }

    // ── 도매 말 → 소매 말 ───────────────────────────────────────

    private static SettlementSummaryLine toLine(WholesaleSettlementSummary source) {
        return new SettlementSummaryLine(
                source.wholesalerId(), source.wholesalerName(), source.balance(),
                source.overdue().amount(), source.overdue().count(), source.overdue().maxDays(),
                source.lastPaidAt(), source.paidLast7Days(),
                source.bankName(), source.bankAccountNo(), source.bankAccountHolder());
    }

    private static LedgerLine toLine(WholesaleSettlementLedgerEntry source) {
        List<LedgerLine.Allocation> allocations = source.allocations() == null ? null
                : source.allocations().stream()
                        .map(a -> new LedgerLine.Allocation(a.retailOrderId(), a.amount()))
                        .toList();
        return new LedgerLine(source.id(), source.kind(), source.date(), source.delta(), source.retailOrderId(),
                source.statementNumber(), source.shippedAt(), source.method(), allocations, source.unallocated());
    }
}
