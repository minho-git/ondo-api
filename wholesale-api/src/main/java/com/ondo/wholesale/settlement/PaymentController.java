package com.ondo.wholesale.settlement;

import com.ondo.wholesale.security.WholesalePrincipal;
import com.ondo.wholesale.settlement.dto.PaymentCreateRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import com.ondo.wholesale.settlement.service.PaymentCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입금 등록 (MUL-124) — 원본 계약: api-lite/07_정산/POST_payments.md (MUL-83 스텁).
 */
@Tag(name = "07 정산")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCommandService paymentCommandService;

    @Operation(summary = "입금 등록 (배분 겸함, Idempotency-Key 필수)", description = """
            "입금만 진행"과 "입금 및 정산"이 같은 엔드포인트 — `allocations`가 비면 선수금.
            `paidBy`(누구 손)와 `method`(무슨 수단)는 다른 축이다. 같은 키 재요청은 200 + 동일 본문(멱등).
            응답의 `ledgerBalance`로 헤더를 재조회 없이 갱신한다 — 음수 = 소매처 채무, 양수 = 선수금.

            배분은 **출고된 금액에만** 붙는다. 주문별 상한 = 출고로 생긴 미수 − 이미 붙은 배분이라
            출고 전 주문은 0 이다(`ALLOCATION_EXCEEDS_OUTSTANDING`). 먼저 받은 돈은 선수금으로 남는다.
            배분 합계 상한은 **이번 입금액 + 남은 선수금**이다 — 이번 입금을 먼저 쓰고 모자라면 오래된 입금부터
            끌어 쓴다. 그래서 `unallocatedAmount`는 이번 입금에서 안 쓴 돈이고, `allocations[].paymentId`가
            이번 입금이 아닐 수 있다. `prepaidRemaining`은 등록 후 거래처 선수금 전체.

            에러: 400 `VALIDATION_FAILED` · `PAID_AT_IN_FUTURE` · `DUPLICATE_ORDER` ·
            `ORDER_RETAILER_MISMATCH` / 404 `RESOURCE_NOT_FOUND` / 409 `IDEMPOTENCY_KEY_REUSED` ·
            `ORDER_NOT_CONFIRMED` · `ALLOCATION_EXCEEDS_PAYMENT` · `ALLOCATION_EXCEEDS_OUTSTANDING`""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "등록됨", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "같은 키 재요청(replay) — 첫 응답과 동일 본문",
                    useReturnTypeSchema = true)})
    @PostMapping("/api/wholesale/payments")
    public ResponseEntity<PaymentCreatedResponse> createPayment(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentCreateRequest request) {
        PaymentCommandService.PaymentResult result =
                paymentCommandService.create(principal.wholesalerId(), idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.response());
    }
}
