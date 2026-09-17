package com.ondo.retail.settlement;

import com.ondo.retail.common.response.ApiResponse;
import com.ondo.retail.settlement.dto.LedgerEntryResponse;
import com.ondo.retail.settlement.dto.PartnerSettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정산 · 미수 (MUL-129). 돈 기록은 도매 DB 에 있고 소매는 읽기만 한다 — 입금 등록은 도매 사장이 한다.
 *
 * <p>소매처 id 는 <b>세션에서만</b> 꺼낸다. 요청에 담긴 값을 믿으면 남의 정산을 볼 수 있다.
 */
@Tag(name = "정산", description = "도매처별 미수 · 연체 · 거래 원장. 도매가 적고 소매는 읽기만 한다.")
@RestController
@RequestMapping("/api/retail/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "도매처별 미수",
               description = """
                       거래한 적 있는 도매처 전부, 빚이 큰 순. `balance` 플러스 = 갚을 돈, 마이너스 = 선수금.
                       연체는 출고마다 기한(출고일 + 외상기간, 당일은 아님)으로 센다. 외상기간은 도매 거래처 설정 전까지
                       모두 1일이다. `paidLast7Days`는 오늘 포함 최근 7일에 보낸 입금 합.""")
    @GetMapping
    public ApiResponse<List<PartnerSettlementResponse>> partners(Authentication authentication) {
        return ApiResponse.of(settlementService.partners(retailerId(authentication)));
    }

    @Operation(summary = "도매처 하나와의 거래 원장",
               description = """
                       출고(`SHIPMENT`, +)와 입금(`PAYMENT`, −)을 오래된 순으로. 취소된 입금은 안 나온다.
                       입금 줄의 `allocations`는 그 입금이 붙은 주문들이다 — 한 입금이 주문 여럿에 붙을 수 있다.
                       `unallocated`는 아직 안 붙은 돈. 거래한 적 없는 도매처면 빈 목록.""")
    @GetMapping("/{wholesalerId}/ledger")
    public ApiResponse<List<LedgerEntryResponse>> ledger(@PathVariable Long wholesalerId,
                                                         Authentication authentication) {
        return ApiResponse.of(settlementService.ledger(retailerId(authentication), wholesalerId));
    }

    private static Long retailerId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
