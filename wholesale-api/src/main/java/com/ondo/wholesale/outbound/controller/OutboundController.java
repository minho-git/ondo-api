package com.ondo.wholesale.outbound.controller;

import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.outbound.OutboundStatusFilter;
import com.ondo.wholesale.outbound.OutboundStubExamples;
import com.ondo.wholesale.outbound.dto.OutboundCreateRequest;
import com.ondo.wholesale.outbound.dto.OutboundCreatedResponse;
import com.ondo.wholesale.outbound.dto.OutboundDetailResponse;
import com.ondo.wholesale.outbound.dto.OutboundRetailerResponse;
import com.ondo.wholesale.outbound.dto.OutboundSummaryResponse;
import com.ondo.wholesale.outbound.dto.PackingItemRowResponse;
import com.ondo.wholesale.outbound.dto.PackingRetailerResponse;
import com.ondo.wholesale.outbound.dto.StatementResponse;
import com.ondo.wholesale.outbound.service.OutboundCommandService;
import com.ondo.wholesale.outbound.service.PackingQueueQueryService;
import com.ondo.wholesale.security.WholesalePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 출고 API (MUL-49) — 원본 계약: api-lite/06_출고.
 *
 * <p>출고 확정(ship)·장끼(statement)는 아직 계약 스텁(MUL-83)이다 — 재고 원장
 * 부품(MUL-72)이 합류한 뒤 실구현으로 교체한다.
 */
@Tag(name = "06 출고")
@RestController
@RequestMapping("/api/wholesale")
@RequiredArgsConstructor
public class OutboundController {

    private final PackingQueueQueryService packingQueueQueryService;
    private final OutboundCommandService outboundCommandService;

    @Operation(summary = "포장 대기 — 소매처 목록 (아코디언 헤더)", description = """
            한 행 = 소매처 하나. 페이징 없음(지금 대기 중인 소매처만이라 수가 제한적).
            소매처명은 검색 대상이 아니다(외부 시스템). 집계는 현재 필터를 반영하니
            펼칠 때 같은 `q`·`receiveBy`를 넘긴다.""")
    @GetMapping("/packing-items/retailers")
    public List<PackingRetailerResponse> packingRetailers(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReceiveBy receiveBy) {
        return packingQueueQueryService.retailers(principal.wholesalerId(), q, receiveBy);
    }

    @Operation(summary = "포장 대기 — 항목 목록 (아코디언 펼침)", description = """
            체크한 행들을 포장 완료로 보낸다 — `id`가 그 요청의 `packingItemIds`.
            페이징 없음: 체크박스로 고른 뒤 한 번에 보내는 화면이라 페이지를 나누면 체크가 날아간다.
            거래 이력 없는 `retailerId`는 404, 필터에 안 걸리면 200 + `[]`.

            에러: 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/packing-items")
    public List<PackingItemRowResponse> packingItems(
            @AuthenticationPrincipal WholesalePrincipal principal,
            @RequestParam(required = false) Long retailerId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReceiveBy receiveBy) {
        return packingQueueQueryService.items(principal.wholesalerId(), retailerId, q, receiveBy);
    }

    @Operation(summary = "출고 — 소매처 목록 (아코디언 헤더)", description = """
            포장 완료 탭(`status=NOT_SHIPPED`)과 출고 완료 탭(`SHIPPED`)이 같은 경로를 쓴다.
            기간 필터 축은 `SHIPPED`면 출고 일시, 그 외에는 포장 일시. 페이징 단위는 소매처.

            에러: 400 `VALIDATION_FAILED`""")
    @GetMapping("/outbounds/retailers")
    public ApiResponse<List<OutboundRetailerResponse>> outboundRetailers(
            @RequestParam(required = false) OutboundStatusFilter status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.paged(
                OutboundStubExamples.outboundRetailers(),
                new ApiResponse.PageMeta(0, 20, 12, 1));
    }

    @Operation(summary = "출고 — 봉투 목록 (아코디언 펼침)", description = """
            헤더에 걸었던 필터를 그대로 함께 보낸다 — 그래야 헤더의 `outboundCount`와 맞는다.
            "출고 완료" 뱃지는 `shippedAt != null`로 판정(응답에 status 필드 없음).
            거래 이력 없는 `retailerId`는 404, 필터에 안 걸리면 200 + `[]`.

            에러: 400 `VALIDATION_FAILED` / 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/outbounds")
    public ApiResponse<List<OutboundSummaryResponse>> outbounds(
            @RequestParam(required = false) Long retailerId,
            @RequestParam(required = false) OutboundStatusFilter status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.paged(
                OutboundStubExamples.outboundSummaries(),
                new ApiResponse.PageMeta(0, 20, 3, 1));
    }

    @Operation(summary = "포장 완료 (봉투 생성)", description = """
            체크한 SKU 행들을 한 봉투로 묶는다 — 재고는 아직 줄지 않는다(차감은 출고 확정).
            전부 같은 소매처·같은 수령 방식이어야 하며, 한 포장의 일부만 담기면 포장이 분할된다
            (대기열에 남는 쪽 id 유지, 나가는 쪽이 새 포장).

            에러: 400 `INVARIANT_VIOLATED` · `DUPLICATE_PACKING_ITEM` · `RETAILER_MIXED` ·
            `RECEIVE_BY_MIXED` / 404 `RESOURCE_NOT_FOUND` / 409 `PACKING_NOT_READY`""")
    @PostMapping("/outbounds")
    @ResponseStatus(HttpStatus.CREATED)
    public OutboundCreatedResponse createOutbound(@AuthenticationPrincipal WholesalePrincipal principal,
                                                  @RequestBody OutboundCreateRequest request) {
        return outboundCommandService.create(principal.wholesalerId(), request);
    }

    @Operation(summary = "출고 상세 (포장 상세 패널)", description = """
            `items`는 SKU 단위로 합친 목록, `packings`는 주문 역추적용.
            `isShippable`은 버튼 활성 판정용이지 성공 보장이 아니다 — 재고 검증이 안 들어 있다.

            에러: 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/outbounds/{outboundId}")
    public OutboundDetailResponse outboundDetail(@PathVariable Long outboundId) {
        return OutboundStubExamples.detailBeforeShip();
    }

    @Operation(summary = "출고 확정 — 재고가 실제로 줄어드는 유일한 지점", description = """
            `shippedAt`과 장끼 번호(`statementNumber`, 날짜별 1부터)가 이 호출로 채워진다.
            이후 봉투는 영구 동결 — 포장 해제도 안 된다. 응답은 출고 상세와 동일 스키마.

            에러: 404 `RESOURCE_NOT_FOUND` / 409 `TRANSITION_NOT_ALLOWED` · `OUTBOUND_EMPTY` ·
            `INSUFFICIENT_STOCK` · `INVARIANT_VIOLATED`""")
    @PostMapping("/outbounds/{outboundId}/ship")
    public OutboundDetailResponse ship(@PathVariable Long outboundId) {
        return OutboundStubExamples.detailAfterShip();
    }

    @Operation(summary = "장끼 (거래명세서)", description = """
            다운로드·인쇄의 원본 데이터 — 렌더·인쇄는 프론트(서버 PDF 없음). 금액 컬럼 없음.
            장끼 번호는 날짜별 1부터라 `shippedAt`과 항상 함께 내려온다(표시 코드 조립용).

            에러: 404 `RESOURCE_NOT_FOUND` (아직 출고 확정 전 포함)""")
    @GetMapping("/outbounds/{outboundId}/statement")
    public StatementResponse statement(@PathVariable Long outboundId) {
        return OutboundStubExamples.statement();
    }
}
