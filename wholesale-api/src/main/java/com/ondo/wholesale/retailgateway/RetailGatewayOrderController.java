package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.retailgateway.dto.RetailOrderCreateRequest;
import com.ondo.wholesale.retailgateway.dto.RetailOrderCreatedResponse;
import com.ondo.wholesale.retailgateway.dto.RetailOrderViewResponse;
import com.ondo.wholesale.retailgateway.dto.RetailWholesalerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** 한 주문서에 도매처가 이렇게 많을 일이 없다. IN 절이 무한정 길어지는 걸 막는다. */
    private static final int MAX_WHOLESALER_IDS = 100;

    /** 내역 한 장이 이보다 길 일이 없다. 소매도 같은 값으로 막지만 여기가 최종 방어다. */
    private static final int MAX_ORDER_IDS = 100;

    private final RetailGatewayOrderService service;
    private final RetailGatewayOrderViewService viewService;

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

    @Operation(summary = "도매처 정보 (소매 백엔드 → 도매)", description = """
            소매 주문서가 쓴다. 상품 목록이 주는 도매처는 id·상호뿐인데 주문서에는
            **입금 계좌**가 더 필요하다 — 도매처마다 따로 입금한다.

            계좌가 비어 있으면 아직 등록을 안 한 도매처다. 소매 화면이 계좌이체를
            못 고르게 막는 근거로 쓴다.

            지워진 도매처도 돌려준다. 지난 주문의 주문서를 다시 열 수 있어야 한다.

            에러: 400 `VALIDATION_FAILED` (`ids` 가 비었거나 100개 초과)""")
    @GetMapping("/api/retail-gateway/wholesalers")
    public List<RetailWholesalerResponse> wholesalers(@RequestParam List<Long> ids) {
        if (ids.isEmpty() || ids.size() > MAX_WHOLESALER_IDS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "ids 는 1개 이상 %d개 이하여야 합니다.".formatted(MAX_WHOLESALER_IDS));
        }
        return service.wholesalers(ids);
    }

    @Operation(summary = "주문 조회 (소매 백엔드 → 도매)", description = """
            소매 주문서 id 로 그 주문서에 딸린 도매처 주문을 전부 내린다. 소매는 주문서
            하나로 받고 도매처별로 잘라 넣으므로, 주문서 하나에 응답이 여럿이다.

            여러 주문서를 한 번에 받는다 — 내역 화면 한 장에 주문서가 스무 개면
            하나씩 부를 때 왕복이 스무 번이 된다.

            **상태를 도매가 만들어 내린다.** DB 는 NEW·CONFIRMED·CANCELLED 셋뿐이고
            출고 진행도를 합쳐 다섯이 된다. 그 규칙을 소매가 또 짜면 두 화면이 같은
            주문을 다르게 부른다. `statusLabel` 도 같은 이유로 같이 준다.

            `retailerId` 로 한 번 더 거른다 — 주문서 id 는 소매가 보낸 값이라
            그것만 믿으면 남의 주문을 읽을 수 있다.

            에러: 400 `VALIDATION_FAILED` (`retailOrderIds` 가 비었거나 100개 초과)""")
    @GetMapping("/api/retail-gateway/orders")
    public List<RetailOrderViewResponse> orders(@RequestParam Long retailerId,
                                                @RequestParam List<Long> retailOrderIds) {
        if (retailOrderIds.isEmpty() || retailOrderIds.size() > MAX_ORDER_IDS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "retailOrderIds 는 1개 이상 %d개 이하여야 합니다.".formatted(MAX_ORDER_IDS));
        }
        return viewService.orders(retailerId, retailOrderIds);
    }
}
