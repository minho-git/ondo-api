package com.ondo.retail.order;

import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.common.response.ApiResponse;
import com.ondo.retail.common.response.PageResponse;
import com.ondo.retail.order.dto.CancelOrderRequest;
import com.ondo.retail.order.dto.CancelOrderResponse;
import com.ondo.retail.order.dto.CheckoutResponse;
import com.ondo.retail.order.dto.OrderDetailResponse;
import com.ondo.retail.order.dto.OrderSummaryResponse;
import com.ondo.retail.order.dto.PlaceOrderRequest;
import com.ondo.retail.order.dto.PlaceOrderResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문. <b>지금은 껍데기다 — 응답 모양만 내고 로직이 없다.</b>
 *
 * <p>주문 접수는 멱등키 · 부분 접수 · 금액 집계가 얽혀 있어 목으로 만들면 나중에 통째로
 * 다시 쓴다. 프론트가 화면 구조를 잡을 수 있게 시그니처와 응답 모양만 확정한다. 로직은 W3.
 */
@RestController
@RequestMapping("/api/retail")
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MockOrderData mock;

    /**
     * 주문서. 장바구니에서 고른 것만 넘긴다.
     *
     * <p>단가를 여기서 다시 받는다 — 담아둔 사이에 가격이 올랐으면 반영돼야 한다.
     */
    @GetMapping("/checkout")
    public ApiResponse<CheckoutResponse> checkout(@RequestParam List<Long> cartItemIds) {
        if (cartItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return ApiResponse.of(mock.checkout());
    }

    /**
     * 주문 접수.
     *
     * <p>일부만 접수돼도 201 이다. 에러가 아니라 결과다. 전부 안 되면 통합 주문을 안 만든다.
     *
     * <p>{@code Idempotency-Key} 는 브라우저가 화면을 열 때 만들어 보낸다. 같은 키로 다시
     * 오면 새로 만들지 않고 처음 결과를 돌려준다 — 연타를 막는다. 지금은 받기만 한다.
     */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlaceOrderResponse> place(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid PlaceOrderRequest request) {
        return ApiResponse.of(mock.place());
    }

    /** 주문 내역. 통합 주문서 하나가 한 줄이다. */
    @GetMapping("/orders")
    public PageResponse<OrderSummaryResponse> orders(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > MAX_PAGE_SIZE || (from != null && to != null && from.isAfter(to))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<OrderSummaryResponse> orders = mock.orders();
        return PageResponse.of(new PageImpl<>(orders, PageRequest.of(page, size), orders.size()));
    }

    /** 주문 상세. 도매처별 주문 · 품목 줄 · 출고 기록. */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> order(@PathVariable Long orderId) {
        return ApiResponse.of(mock.detail(orderId));
    }

    /**
     * 주문 취소. 도매처별로 취소한다 — 통째 취소는 없다.
     *
     * <p>일부만 취소돼도 200 이다. NEW 인 것만 취소된다.
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<CancelOrderResponse> cancel(@PathVariable Long orderId,
                                                   @RequestBody(required = false) CancelOrderRequest request) {
        return ApiResponse.of(mock.cancel());
    }
}
