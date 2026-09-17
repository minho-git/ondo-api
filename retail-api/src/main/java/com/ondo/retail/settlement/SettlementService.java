package com.ondo.retail.settlement;

import com.ondo.retail.backorder.OrderNoQuery;
import com.ondo.retail.settlement.dto.LedgerEntryResponse;
import com.ondo.retail.settlement.dto.LedgerLine;
import com.ondo.retail.settlement.dto.PartnerSettlementResponse;
import com.ondo.retail.settlement.dto.SettlementSummaryLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 정산 · 미수 (MUL-129). 숫자는 도매가 계산하고, 여기서는 소매 말로 옮기며 주문번호 · 장끼 번호를 채운다.
 *
 * <p><b>{@code @Transactional} 이 없다.</b> 미송({@code BackorderService})과 같은 이유 — 도매 호출을 트랜잭션 안에서
 * 기다리면 도매가 느려질 때 소매 커넥션 풀이 마른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STATEMENT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SettlementClient client;
    private final OrderNoQuery orderNoQuery;

    /** @param retailerId 세션에서 꺼낸 값이어야 한다 */
    public List<PartnerSettlementResponse> partners(Long retailerId) {
        return client.summaries(retailerId).stream().map(SettlementService::toResponse).toList();
    }

    /** @param retailerId 세션에서 꺼낸 값이어야 한다 */
    public List<LedgerEntryResponse> ledger(Long retailerId, Long wholesalerId) {
        List<LedgerLine> lines = client.ledger(retailerId, wholesalerId);

        List<Long> orderIds = lines.stream()
                .flatMap(line -> Stream.concat(
                        Stream.ofNullable(line.orderId()),
                        line.allocations() == null ? Stream.empty()
                                : line.allocations().stream().map(LedgerLine.Allocation::orderId)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> orderNos = orderNoQuery.byOrderIds(retailerId, orderIds);

        return lines.stream().map(line -> toResponse(line, orderNos)).toList();
    }

    private static PartnerSettlementResponse toResponse(SettlementSummaryLine line) {
        PartnerSettlementResponse.Bank bank = line.bankAccountNo() == null ? null
                : new PartnerSettlementResponse.Bank(line.bankName(), line.bankAccountNo(), line.bankAccountHolder());
        return new PartnerSettlementResponse(
                line.wholesalerId(), line.wholesalerName(), line.balance(),
                new PartnerSettlementResponse.Overdue(line.overdueAmount(), line.overdueCount(), line.overdueMaxDays()),
                line.lastPaidAt(), line.paidLast7Days(), bank);
    }

    private static LedgerEntryResponse toResponse(LedgerLine line, Map<Long, String> orderNos) {
        if ("SHIPMENT".equals(line.kind())) {
            return new LedgerEntryResponse(line.id(), line.date(), "SHIPMENT",
                    statementNo(line), orderNo(line.orderId(), orderNos, line.id()), null, line.delta(), null, null);
        }
        List<LedgerEntryResponse.Allocation> allocations = line.allocations() == null ? List.of()
                : line.allocations().stream()
                        .map(a -> new LedgerEntryResponse.Allocation(orderNo(a.orderId(), orderNos, line.id()), a.amount()))
                        .toList();
        return new LedgerEntryResponse(line.id(), line.date(), "PAYMENT", null, null,
                method(line.method()), line.delta(), allocations, line.unallocated());
    }

    /** 장끼 표시 코드 {@code JG-출고일(KST)-순번 3자리}. 도매 화면과 같은 조립이다. */
    static String statementNo(LedgerLine line) {
        if (line.statementNumber() == null || line.shippedAt() == null) {
            return null;
        }
        return "JG-%s-%03d".formatted(
                line.shippedAt().atZoneSameInstant(KST).format(STATEMENT_DATE), line.statementNumber());
    }

    /** 소매 화면 결제 수단은 {@code CASH} · {@code TRANSFER} 둘이다. */
    private static String method(String wholesaleMethod) {
        return "BANK_TRANSFER".equals(wholesaleMethod) ? "TRANSFER" : wholesaleMethod;
    }

    /**
     * 주문번호를 못 찾아도 줄을 버리지 않는다 — 버리면 잔액이 안 맞는다. 도매 화면에서 직접 넣은 주문이거나
     * 도매 · 소매 주문서가 어긋난 경우라 로그를 남긴다.
     */
    private static String orderNo(Long orderId, Map<Long, String> orderNos, Long ledgerId) {
        if (orderId == null) {
            return null;
        }
        String orderNo = orderNos.get(orderId);
        if (orderNo == null) {
            log.warn("정산 원장에 걸린 주문서를 소매에서 못 찾았다. ledgerId={} orderId={}", ledgerId, orderId);
        }
        return orderNo;
    }
}
