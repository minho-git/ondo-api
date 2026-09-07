package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.AllocationBatchResponse;
import com.ondo.wholesale.backorder.dto.BackorderAllocationRequest;
import com.ondo.wholesale.backorder.dto.BackorderListResponse;
import com.ondo.wholesale.backorder.dto.BackorderSkuResponse;
import com.ondo.wholesale.backorder.dto.ExpectedInboundRequest;
import com.ondo.wholesale.backorder.dto.ExpectedInboundResponse;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.security.WholesalePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 미송 API (MUL-48) — 원본 계약: api-lite/05_미송.
 */
@Tag(name = "05 미송")
@RestController
@RequestMapping("/api/wholesale")
@RequiredArgsConstructor
public class BackorderController {

    private final BackorderQueryService backorderQueryService;
    private final BackorderAllocationService backorderAllocationService;
    private final ExpectedInboundService expectedInboundService;

    @Operation(summary = "미송 SKU 목록 (아코디언 헤더)", description = """
            미송이 남은 SKU 만 나온다 — 전부 해소된 SKU 는 빠진다. 기본 정렬
            `latestBackorderedAt,desc`(가장 최근에 미송이 쌓인 SKU 먼저).
            정렬 키는 `latestBackorderedAt`·`backorderQty` 둘이다.

            에러: 400 `VALIDATION_FAILED` (`size > 100` / 모르는 정렬 키)""")
    @GetMapping("/backorders/variants")
    public ApiResponse<List<BackorderSkuResponse>> backorderSkus(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return backorderQueryService.skuList(principal.wholesalerId(), q, page, size, sort);
    }

    @Operation(summary = "SKU별 미송 목록 + 요약 (아코디언 펼침)", description = """
            그 SKU 를 기다리는 주문들을 FIFO(`createdAt,asc`)로 내린다 — 제안일 뿐 강제가 아니다.
            페이징 없음: 배분 입력칸을 전부 그린 뒤 한 번에 확정하는 화면이라서다.
            응답은 `meta` 대신 `data + stats` — 좌측 표와 우측 요약 패널을 호출 한 번으로 채운다.

            에러: 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/variants/{variantId}/backorders")
    public BackorderListResponse backordersOfSku(@AuthenticationPrincipal WholesalePrincipal principal,
                                                 @PathVariable Long variantId,
                                                 @RequestParam(required = false) String sort) {
        return backorderQueryService.backordersOfSku(principal.wholesalerId(), variantId, sort);
    }

    @Operation(summary = "미송 배분 (배분 확정)", description = """
            여러 주문의 미송에 재고를 나눠 준다 — 주문마다 포장 카드가 한 장씩 생긴다.
            부분 성공 없음: 한 건이라도 걸리면 전체 롤백, `errors[]`에 걸린 항목 전부.
            `resolvedBackorderIds`로 해소된 미송을 알려준다(프론트가 잔여 계산으로 판정하지 않게).

            에러: 400 `INVARIANT_VIOLATED` · `DUPLICATE_BACKORDER` / 404 `RESOURCE_NOT_FOUND` /
            409 `BACKORDER_NOT_OPEN` · `ALLOCATION_EXCEEDS_REMAINING` · `ALLOCATION_EXCEEDS_ORDER` ·
            `INSUFFICIENT_STOCK`(합계 초과라 field 가 `items`)""")
    @PostMapping("/backorders/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationBatchResponse allocate(@AuthenticationPrincipal WholesalePrincipal principal,
                                            @RequestBody BackorderAllocationRequest request) {
        return backorderAllocationService.allocate(principal.wholesalerId(), request);
    }

    @Operation(summary = "예상 입고일 등록 (전체 대체)", description = """
            SKU 에 붙는 값이라 미송과 독립. `expectedInboundDate: null` = 해제(사유도 함께 null).
            PUT 인 이유 — 날짜를 바꿨는데 예전 사유가 남는 건 틀린 상태라 부분 갱신을 계약에 안 넣었다.
            이력은 남지 않는다(최신값 하나).

            에러: 400 `VALIDATION_FAILED` / 404 `RESOURCE_NOT_FOUND`""")
    @PutMapping("/variants/{variantId}/expected-inbound")
    public ExpectedInboundResponse registerExpectedInbound(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @PathVariable Long variantId,
            @RequestBody ExpectedInboundRequest request) {
        return expectedInboundService.register(principal.wholesalerId(), variantId, request);
    }
}
