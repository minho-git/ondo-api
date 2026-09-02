package com.ondo.wholesale.settlement;

import com.ondo.wholesale.settlement.dto.PaymentCreateRequest;
import com.ondo.wholesale.settlement.dto.PaymentCreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입금 등록 계약 스텁 (MUL-83) — 원본: api-lite/07_정산/POST_payments.md.
 * example 응답만 반환하며 실구현이 서비스 계층으로 교체한다.
 */
@Tag(name = "07 정산")
@RestController
public class PaymentController {

    @Operation(summary = "입금 등록 (배분 겸함, Idempotency-Key 필수)", description = """
            "입금만 진행"과 "입금 및 정산"이 같은 엔드포인트 — `allocations`가 비면 선수금.
            `paidBy`(누구 손)와 `method`(무슨 수단)는 다른 축이다. 같은 키 재요청은 200 + 동일 본문(멱등).
            응답의 `ledgerBalance`로 헤더를 재조회 없이 갱신한다.

            에러: 400 `VALIDATION_FAILED` · `PAID_AT_IN_FUTURE` · `DUPLICATE_ORDER` ·
            `ORDER_RETAILER_MISMATCH` / 404 `RESOURCE_NOT_FOUND` / 409 `IDEMPOTENCY_KEY_REUSED` ·
            `STATE_CONFLICT` · `ORDER_NOT_CONFIRMED` · `ALLOCATION_EXCEEDS_PAYMENT` ·
            `ALLOCATION_EXCEEDS_OUTSTANDING`""")
    @PostMapping("/api/wholesale/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCreatedResponse createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentCreateRequest request) {
        return SettlementStubExamples.createdPayment();
    }
}
