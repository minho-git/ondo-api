package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.retailgateway.dto.RetailSettlementLedgerResponse;
import com.ondo.wholesale.retailgateway.dto.RetailSettlementSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 소매 정산 조회 — 소매 백엔드만 부른다 (MUL-129).
 */
@Tag(name = "08 소매접점")
@RestController
@RequestMapping("/api/retail-gateway")
@RequiredArgsConstructor
public class RetailGatewaySettlementController {

    private final RetailGatewaySettlementService service;

    @Operation(summary = "도매처별 정산 요약 (소매 백엔드 → 도매)", description = """
            그 소매처와 거래 관계가 있는 도매처 전부 — 거래 관계는 주문이 들어와야 생긴다. 빚이 큰 순.

            부호는 소매 화면 기준: `balance` 플러스 = 갚을 돈, 마이너스 = 선수금.
            연체는 출고마다 기한(출고일 + 외상기간, 당일은 연체 아님)으로 센다. 외상기간은 거래처 설정(MUL-128)
            전까지 모든 주문 1일이다. `paidLast7Days`는 오늘 포함 최근 7일 입금 합. 취소된 입금은 어디에도 안 센다.""")
    @GetMapping("/settlements")
    public List<RetailSettlementSummaryResponse> summaries(@RequestParam Long retailerId) {
        return service.summaries(retailerId);
    }

    @Operation(summary = "도매처 하나와의 거래 원장 (소매 백엔드 → 도매)", description = """
            출고(`SHIPMENT`, +)와 입금(`PAYMENT`, −)을 오래된 순으로. 취소된 입금은 그 줄과 취소 줄을 둘 다 뺀다.

            주문번호는 안 온다 — `retailOrderId`로 소매가 채운다. 장끼 표시 코드도 `shippedAt` · `statementNumber`로
            소매가 조립한다. 입금 줄의 `allocations`는 한 입금이 붙은 주문들이고 `unallocated`는 안 붙은 돈이다.
            거래 관계가 없는 도매처면 빈 목록.""")
    @GetMapping("/settlements/ledger")
    public List<RetailSettlementLedgerResponse> ledger(@RequestParam Long retailerId,
                                                       @RequestParam Long wholesalerId) {
        return service.ledger(retailerId, wholesalerId);
    }
}
