package com.ondo.wholesale.settlement;

import com.ondo.wholesale.security.WholesalePrincipal;
import com.ondo.wholesale.settlement.dto.AllocationCancelledResponse;
import com.ondo.wholesale.settlement.dto.AllocationCreateRequest;
import com.ondo.wholesale.settlement.dto.AllocationCreatedResponse;
import com.ondo.wholesale.settlement.dto.PrepaidSummaryResponse;
import com.ondo.wholesale.settlement.service.AllocationCommandService;
import com.ondo.wholesale.settlement.service.PrepaidQueryService;
import com.ondo.wholesale.settlement.service.SettlementCancelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 선수금 — 입금 없이 정산 · 선수금 요약 (MUL-125). 화면 요청: ondo-web #138.
 */
@Tag(name = "07 정산")
@RestController
@RequestMapping("/api/wholesale")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationCommandService allocationCommandService;
    private final PrepaidQueryService prepaidQueryService;
    private final SettlementCancelService settlementCancelService;

    @Operation(summary = "선수금으로 정산 (Idempotency-Key 필수)", description = """
            새 입금 없이 받아 둔 선수금을 출고된 주문에 붙인다 — 원장은 안 바뀐다.
            미송 선결제처럼 돈이 먼저 오고 물건이 나중에 나간 주문을 출고 뒤 정산하는 길이다.

            돈은 **오래된 입금부터** 꺼낸다. 한 주문이 입금 여럿에 걸치면 `allocations`에 줄이 여럿이고
            줄마다 `paymentId`가 다르다. 주문별 상한은 입금 등록과 같다(출고로 생긴 미수 − 이미 붙은 배분).
            같은 키 재요청은 200 + 동일 본문(멱등).

            에러: 400 `VALIDATION_FAILED` · `DUPLICATE_ORDER` · `ORDER_RETAILER_MISMATCH` /
            404 `RESOURCE_NOT_FOUND` / 409 `IDEMPOTENCY_KEY_REUSED` · `ORDER_NOT_CONFIRMED` ·
            `ALLOCATION_EXCEEDS_PREPAID` · `ALLOCATION_EXCEEDS_OUTSTANDING`""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "정산됨", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "같은 키 재요청(replay) — 첫 응답과 동일 본문",
                    useReturnTypeSchema = true)})
    @PostMapping("/allocations")
    public ResponseEntity<AllocationCreatedResponse> createAllocation(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AllocationCreateRequest request) {
        AllocationCommandService.AllocationResult result =
                allocationCommandService.create(principal.wholesalerId(), idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.response());
    }

    @Operation(summary = "배분 취소", description = """
            주문에 붙인 돈을 떼어낸다 (MUL-127). 줄은 지우지 않고 취소 표시만 한다. 원장은 안 바뀐다 —
            돈은 그대로 받은 상태이고, 그 금액은 **선수금으로 돌아간다**. 주문의 남은 미수가 다시 늘어난다.
            금액을 고치려면 취소하고 입금 등록 · 선수금 정산으로 다시 붙인다.

            에러: 404 `RESOURCE_NOT_FOUND` / 409 `STATE_CONFLICT`(이미 취소된 배분 · 취소된 입금의 배분)""")
    @PostMapping("/allocations/{allocationId}/cancel")
    public AllocationCancelledResponse cancelAllocation(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @PathVariable Long allocationId) {
        return settlementCancelService.cancelAllocation(principal.wholesalerId(), allocationId);
    }

    @Operation(summary = "선수금 요약 (정산 탭 3카드)", description = """
            총 입금액 · 배분 완료액 · 남은 선수금. 취소된 입금과 취소된 배분은 뺀다.
            `prepaid = totalPaid − totalAllocated`. 입금 등록 폼의 사용 가능액은 이번 입금액 + `prepaid`다.

            에러: 404 `RESOURCE_NOT_FOUND`(거래 관계가 없는 소매처)""")
    @GetMapping("/receivables/retailers/{retailerId}/prepaid")
    public PrepaidSummaryResponse prepaidSummary(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @PathVariable Long retailerId) {
        return prepaidQueryService.summary(principal.wholesalerId(), retailerId);
    }
}
