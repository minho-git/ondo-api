package com.ondo.wholesale.settlement;

import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.security.WholesalePrincipal;
import com.ondo.wholesale.settlement.dto.ReceivableLedgerResponse;
import com.ondo.wholesale.settlement.dto.ReceivableRetailerResponse;
import com.ondo.wholesale.settlement.service.ReceivableQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 미수 조회 (MUL-126) — 원본 계약: api-lite/07_정산 (MUL-83 스텁). 정산 탭의 [정산 상태] 세그먼트는
 * 여기가 아니라 GET /orders(04_주문)다 — 자원이 달라 엔드포인트도 다르다.
 */
@Tag(name = "07 정산")
@RestController
@RequestMapping("/api/wholesale")
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableQueryService receivableQueryService;

    @Operation(summary = "미수 — 소매처 목록 (아코디언 헤더)", description = """
            `ledgerBalance`는 부호 그대로 — 음수 = 소매처 채무, 양수 = 선수금(표기를 뒤집으면 안 된다).
            `orderCount`는 확정 주문 수. 확정 주문도 오간 돈도 없는 소매처는 목록에 없다.
            검색(q) 파라미터 없음 — 소매처 상호는 도매 DB 밖, 미수엔 품명이 없다.
            `retailerCode`는 출처가 아직 없어 null 이다. `lastOccurredAt`은 거래가 없으면 null.

            정렬: `ledgerBalance`(기본 `ledgerBalance,asc` = 빚이 큰 순) · `lastOccurredAt` · `retailerName`.

            에러: 400 `VALIDATION_FAILED`(`size > 100` · 모르는 정렬 키)""")
    @GetMapping("/receivables/retailers")
    public ApiResponse<List<ReceivableRetailerResponse>> receivableRetailers(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return receivableQueryService.retailers(principal.wholesalerId(), page, size, sort);
    }

    @Operation(summary = "미수원장 (아코디언 펼침)", description = """
            판매·입금을 시간순으로(기본 최신순, `sort=occurredAt,asc`로 오래된 순). 화면 하단 "현재 잔액"은
            `meta.ledgerBalance`(전체 기준 최신값)를 쓴다 — `data[0].balanceAfter`는 페이지·필터에 따라
            과거 시점 값이라 틀린다. `balanceAfter`는 그 줄을 등록한 순간의 잔액이다.
            404 없음 — 거래 이력 없는 `retailerId`도 200 + `[]` + 잔액 0.

            `orderId`·`orderNumber`는 SALE 줄만, `paymentId`는 PAYMENT·PAYMENT_VOID 줄만 값이 있고 나머지는 null.

            에러: 400 `VALIDATION_FAILED`(`retailerId` 없음 · 모르는 `entryType` · `size > 100` · 모르는 정렬)""")
    @GetMapping("/receivables")
    public ReceivableLedgerResponse ledger(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false) Long retailerId,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return receivableQueryService.ledger(principal.wholesalerId(), retailerId, entryType, from, to,
                page, size, sort);
    }
}
