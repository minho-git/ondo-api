package com.ondo.wholesale.order.controller;

import com.ondo.wholesale.order.OrderFilterKey;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.order.dto.request.OrderConfirmRequest;
import com.ondo.wholesale.order.service.OrderCommandService;
import com.ondo.wholesale.order.service.OrderListQuery;
import com.ondo.wholesale.order.service.OrderQueryService;
import com.ondo.wholesale.security.WholesalePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ondo.wholesale.order.dto.response.OrderDetailResponse;
import com.ondo.wholesale.order.dto.response.OrderFilterResponse;
import com.ondo.wholesale.order.dto.response.OrderSummaryResponse;
import com.ondo.wholesale.order.dto.request.PackingCreateRequest;
import com.ondo.wholesale.order.dto.response.PackingCreatedResponse;
import com.ondo.wholesale.order.dto.response.PackingQueueItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 주문 API (MUL-47) — 원본 계약: api-lite/04_주문.
 * 조회 3종과 확정·취소는 실구현이고, 포장 준비·대기열은 아직 계약 스텁(MUL-82)이라 example 응답을 반환한다.
 */
@Tag(name = "04 주문")
@RestController
@RequestMapping("/api/wholesale/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    @Operation(summary = "주문 목록", description = """
            주문 탭 리스트와 정산 탭 [정산 상태] 세그먼트가 같은 스키마를 쓴다 — 거는 필터만 다르다.
            `filter`는 상태 칩의 key(주문 탭 전용), `retailerId`를 넣으면 확정 주문만 내려온다(정산 탭 전용).
            버튼 노출은 `isConfirmable`·`isCancellable`·`isPackable` boolean 으로만 판단한다.
            `meta.totalElements`는 페이지네이션 전용 — 칩 건수로 쓰지 않는다.

            에러: 400 `VALIDATION_FAILED` (`from > to` / 정의되지 않은 `filter`·`settlementStatus` / `size > 100`)""")
    @GetMapping
    public ApiResponse<List<OrderSummaryResponse>> list(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false) OrderFilterKey filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long retailerId,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        OrderListQuery query = OrderListQuery.of(filter, q, retailerId, settlementStatus,
                from, to, page, size, sort);
        return orderQueryService.list(principal.wholesalerId(), query);
    }

    @Operation(summary = "상태 칩 (필터 리스트)", description = """
            칩 목록·라벨·건수를 함께 내린다. 배열 순서가 곧 칩 표시 순서.
            `filter`는 받지 않는다 — 목록의 필터를 여기에 걸면 칩 건수가 자기 필터에 갇힌다.
            `q`·`from`/`to`는 목록과 같은 값으로 함께 보낸다.

            에러: 400 `VALIDATION_FAILED` (`from > to`)""")
    @GetMapping("/filters")
    public List<OrderFilterResponse> filters(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderQueryService.filters(principal.wholesalerId(), q, from, to);
    }

    @Operation(summary = "주문 상세", description = """
            라인·배분·미송 현황. `items[].variantAvailableQty`만 SKU 스코프이고 나머지 수량은 라인 스코프.
            미송 상세·포장 대기 카드는 이 응답에 없다.

            에러: 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/{orderId}")
    public OrderDetailResponse detail(@AuthenticationPrincipal WholesalePrincipal principal,
                                      @PathVariable Long orderId) {
        return orderQueryService.detail(principal.wholesalerId(), orderId);
    }

    @Operation(summary = "주문 확정 (확정 + 배분 + 미송 생성)", description = """
            한 트랜잭션에 확정·배분·미송 생성을 처리한다. `items`는 주문의 전 라인 필수,
            `allocateQty: 0`은 정상값(그 라인 전량 미송). 배분되지 않은 잔량은 전부 미송이 된다.
            응답은 주문 상세와 동일한 스키마 — 재조회 없이 화면을 갱신한다.

            에러: 400 `ORDER_ITEM_MISSING` · `ORDER_ITEM_NOT_IN_ORDER` · `DUPLICATE_ORDER_ITEM` ·
            `INVARIANT_VIOLATED` · `ALLOCATION_EXCEEDS_ORDER` / 404 `RESOURCE_NOT_FOUND` /
            409 `TRANSITION_NOT_ALLOWED` · `INSUFFICIENT_STOCK`""")
    @PostMapping("/{orderId}/confirm")
    public OrderDetailResponse confirm(@AuthenticationPrincipal WholesalePrincipal principal,
                                       @PathVariable Long orderId,
                                       @RequestBody OrderConfirmRequest request) {
        return orderCommandService.confirm(principal.wholesalerId(), orderId, request);
    }

    @Operation(summary = "주문 취소 (NEW 전용)", description = """
            확정된 주문은 취소할 수 없다 — 이미 미수·배분·미송이 달려 있다.
            응답은 주문 상세와 동일한 스키마, `status`만 CANCELLED.

            에러: 404 `RESOURCE_NOT_FOUND` / 409 `TRANSITION_NOT_ALLOWED`""")
    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal WholesalePrincipal principal,
                                      @PathVariable Long orderId) {
        return orderCommandService.cancel(principal.wholesalerId(), orderId);
    }

    @Operation(summary = "포장 준비 (추가 배분)", description = """
            한 요청 = 포장 대기 카드 1장. 배분할 라인만 담고 `allocateQty >= 1`.
            미송 id 는 담지 않는다 — 라인당 OPEN 미송이 최대 1건이라 서버가 찾아 연결한다.
            배분은 재고를 줄이지 않는다 — 실물이 나가는 것은 출고다.

            에러: 400 `ORDER_ITEM_NOT_IN_ORDER` · `DUPLICATE_ORDER_ITEM` · `INVARIANT_VIOLATED` ·
            `ALLOCATION_EXCEEDS_ORDER` / 404 `RESOURCE_NOT_FOUND` / 409 `TRANSITION_NOT_ALLOWED` ·
            `INSUFFICIENT_STOCK` · `ALLOCATION_EXCEEDS_REMAINING`""")
    @PostMapping("/{orderId}/packings")
    @ResponseStatus(HttpStatus.CREATED)
    public PackingCreatedResponse createPacking(@PathVariable Long orderId,
                                                @RequestBody PackingCreateRequest request) {
        return OrderStubExamples.createdPacking();
    }

    @Operation(summary = "포장 대기열", description = """
            카드 하나 = 포장 하나. 취소된 카드는 나오지 않는다. 페이징 없음.
            삭제 버튼 활성 조건은 `isCancellable` 그대로 쓴다.""")
    @GetMapping("/{orderId}/packings")
    public List<PackingQueueItemResponse> packingQueue(
            @PathVariable Long orderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort) {
        return OrderStubExamples.packingQueue();
    }
}
