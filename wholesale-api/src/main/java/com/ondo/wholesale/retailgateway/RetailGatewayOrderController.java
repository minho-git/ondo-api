package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.order.OrderStatusKey;
import com.ondo.wholesale.retailgateway.dto.RetailOrderCreateRequest;
import com.ondo.wholesale.retailgateway.dto.RetailOrderCreatedResponse;
import com.ondo.wholesale.retailgateway.dto.RetailOrderItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 소매 접점 계약 스텁 (MUL-82) — 원본: api/08_소매접점.md §2.
 *
 * <p>인증 주체가 도매처 본인이 아니라 소매 백엔드라 경로가 {@code /api/wholesale} 밖이다.
 * 인증 축은 미확정(U-14-B2) — 결정 전까지 잠정 permitAll (SecurityConfig 참조).
 * 소매는 통합 주문서를 도매처별로 잘라 이 API 를 N 번 호출한다(부분 성공 허용).
 */
@Tag(name = "08 소매접점")
@RestController
public class RetailGatewayOrderController {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Operation(summary = "주문 생성 (소매 백엔드 → 도매)", description = """
            단일 도매처 주문만 받는다 — 원자성 단위 = 도매처별 주문. 재고는 검증하지 않는다(D-062,
            재고 반영은 확정 시점의 수동 배분). `expectedUnitPrice`는 현재 판매가와 대조해
            어긋나면 409 로 되돌린다. 중복 주문은 UNIQUE(retailOrderId, wholesalerId)가 막는다.

            에러: 400 `VARIANT_WHOLESALER_MISMATCH` · `VALIDATION_FAILED` · `INVARIANT_VIOLATED` /
            409 `LISTING_NOT_ON_SALE` · `PRICE_NOT_SET` · `ORDER_LIMIT_EXCEEDED` ·
            `PRICE_CHANGED` · `ORDER_ALREADY_CREATED`""")
    @PostMapping("/api/retail-gateway/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public RetailOrderCreatedResponse create(@RequestBody RetailOrderCreateRequest request) {
        return new RetailOrderCreatedResponse(
                5531L, 42, 90210L, OrderStatusKey.NEW, 25000,
                OffsetDateTime.of(2026, 8, 19, 14, 30, 0, 0, KST),
                List.of(
                        new RetailOrderItemResponse(8801L, 1042L, 3, 5000),
                        new RetailOrderItemResponse(8802L, 1043L, 2, 5000)));
    }
}
