package com.ondo.wholesale.backorder.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 미송 배분 201 응답. {@code allocationBatchId}는 "이 클릭 하나"를 가리키고,
 * {@code resolvedBackorderIds}는 잔여가 0이 되어 해소된 미송 — 프론트가 잔여를 계산해
 * 해소 여부를 판정하지 않게 서버가 준다. 일괄 취소 API 는 없다(카드별 배분 취소 N번).
 */
public record AllocationBatchResponse(
        Long allocationBatchId,
        OffsetDateTime createdAt,
        List<AllocationPackingResponse> packings,
        List<Long> resolvedBackorderIds
) {}
