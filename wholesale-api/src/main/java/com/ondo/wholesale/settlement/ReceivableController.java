package com.ondo.wholesale.settlement;

import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.settlement.dto.ReceivableLedgerResponse;
import com.ondo.wholesale.settlement.dto.ReceivableRetailerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 미수 조회 계약 스텁 (MUL-83) — 원본: api-lite/07_정산. 정산 탭의 [정산 상태] 세그먼트는
 * 여기가 아니라 GET /orders(04_주문)다 — 자원이 달라 엔드포인트도 다르다.
 */
@Tag(name = "07 정산")
@RestController
@RequestMapping("/api/wholesale")
public class ReceivableController {

    @Operation(summary = "미수 — 소매처 목록 (아코디언 헤더)", description = """
            `ledgerBalance`는 부호 그대로 — 음수 = 소매처 채무, 양수 = 선수금(표기를 뒤집으면 안 된다).
            확정 주문만 센다. 검색(q) 파라미터 없음 — 소매처 상호는 도매 DB 밖, 미수엔 품명이 없다.

            에러: 400 `VALIDATION_FAILED` · `SORT_NOT_ALLOWED`""")
    @GetMapping("/receivables/retailers")
    public ApiResponse<List<ReceivableRetailerResponse>> receivableRetailers(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.paged(
                SettlementStubExamples.receivableRetailers(),
                new ApiResponse.PageMeta(0, 20, 12, 1));
    }

    @Operation(summary = "미수원장 (아코디언 펼침)", description = """
            판매·입금을 시간순으로. 화면 하단 "현재 잔액"은 `meta.ledgerBalance`(전체 기준 최신값)를
            쓴다 — `data[0].balanceAfter`는 페이지·필터에 따라 과거 시점 값이라 틀린다.
            404 없음 — 거래 이력 없는 `retailerId`도 200 + `[]` + 잔액 0.

            에러: 400 `VALIDATION_FAILED`""")
    @GetMapping("/receivables")
    public ReceivableLedgerResponse ledger(
            @RequestParam(required = false) Long retailerId,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return SettlementStubExamples.ledger();
    }
}
