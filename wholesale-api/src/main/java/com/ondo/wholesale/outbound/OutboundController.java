package com.ondo.wholesale.outbound;

import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.outbound.dto.OutboundRetailerResponse;
import com.ondo.wholesale.outbound.dto.OutboundSummaryResponse;
import com.ondo.wholesale.outbound.dto.PackingItemRowResponse;
import com.ondo.wholesale.outbound.dto.PackingRetailerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 출고 계약 스텁 (MUL-83) — 원본: api-lite/06_출고. example 응답만 반환하며
 * 실구현이 서비스 계층으로 교체한다. 인증·봉투는 실서버와 동일하게 동작한다.
 */
@Tag(name = "06 출고")
@RestController
@RequestMapping("/api/wholesale")
public class OutboundController {

    @Operation(summary = "포장 대기 — 소매처 목록 (아코디언 헤더)", description = """
            한 행 = 소매처 하나. 페이징 없음(지금 대기 중인 소매처만이라 수가 제한적).
            소매처명은 검색 대상이 아니다(외부 시스템). 집계는 현재 필터를 반영하니
            펼칠 때 같은 `q`·`receiveBy`를 넘긴다.""")
    @GetMapping("/packing-items/retailers")
    public List<PackingRetailerResponse> packingRetailers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReceiveBy receiveBy) {
        return OutboundStubExamples.packingRetailers();
    }

    @Operation(summary = "포장 대기 — 항목 목록 (아코디언 펼침)", description = """
            체크한 행들을 포장 완료로 보낸다 — `id`가 그 요청의 `packingItemIds`.
            페이징 없음: 체크박스로 고른 뒤 한 번에 보내는 화면이라 페이지를 나누면 체크가 날아간다.
            거래 이력 없는 `retailerId`는 404, 필터에 안 걸리면 200 + `[]`.

            에러: 404 `RESOURCE_NOT_FOUND`""")
    @GetMapping("/packing-items")
    public List<PackingItemRowResponse> packingItems(
            @RequestParam(required = false) Long retailerId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReceiveBy receiveBy) {
        return OutboundStubExamples.packingItems();
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
}
