package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.retailgateway.dto.RetailOrderCreateRequest;
import com.ondo.wholesale.retailgateway.dto.RetailOrderCreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소매 주문 접수 (MUL-98) — 소매 백엔드가 부른다. 원본: api/08_소매접점.md §2.
 *
 * <p>계약 스텁(MUL-82)을 걷어내고 실구현으로 바꿨다.
 *
 * <p>{@code /api/retail-gateway/**} 는 MUL-87 의 공유 시크릿으로 잠겨 있다
 * ({@link GatewaySecretAuthorizationManager}). 소매 백엔드만 들어온다.
 *
 * <p>채빈의 {@code OrderCommandService} 와 겹치지 않는다 — 그쪽은 도매가 자기 화면에서
 * 확정·취소·배분하는 것이고, 여기는 소매가 주문을 넣는 입구다. 소매는 통합 주문서를
 * 도매처별로 잘라 이 API 를 N 번 호출한다(부분 성공 허용).
 */
@Tag(name = "08 소매접점")
@RestController
@RequiredArgsConstructor
public class RetailGatewayOrderController {

    private final RetailGatewayOrderService service;

    @Operation(summary = "주문 생성 (소매 백엔드 → 도매)", description = """
            단일 도매처 주문만 받는다 — 원자성 단위 = 도매처별 주문. 재고는 검증하지 않는다(D-062,
            재고 반영은 확정 시점의 수동 배분). `expectedUnitPrice`는 현재 판매가와 대조해
            어긋나면 409 로 되돌린다. 중복 주문은 UNIQUE(retailOrderId, wholesalerId)가 막는다.

            `retailerName`·`retailerPhone`·`agentName`·`agentPhone` 은 도매가 소매 DB 를 못 읽어서
            받는 스냅샷이다. 그중 `retailerName` 은 첫 거래에서 거래처를 만들 때 쓰므로 필수다.

            에러: 400 `VARIANT_WHOLESALER_MISMATCH` · `VALIDATION_FAILED` · `DUPLICATE_ORDER_ITEM` /
            409 `LISTING_NOT_ON_SALE` · `PRICE_NOT_SET` · `ORDER_LIMIT_EXCEEDED` ·
            `PRICE_CHANGED` · `ORDER_ALREADY_CREATED`""")
    @PostMapping("/api/retail-gateway/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public RetailOrderCreatedResponse create(@RequestBody RetailOrderCreateRequest request) {
        return service.create(request);
    }
}
