package com.ondo.wholesale.backorder.dto;

import java.util.List;

/**
 * 미송 배분 요청 (api-lite/05_미송/POST_backorders_allocations.md). 화면의 "배분 확정".
 *
 * <p>{@code allocateQty >= 1} — 입력칸이 0인 행은 프론트가 걸러 보낸다. 부분 성공 없음:
 * 한 건이라도 검증에 걸리면 전체 롤백(사장님이 한 덩어리로 판단한 배치라서).
 */
public record BackorderAllocationRequest(List<BackorderAllocationItem> items) {

    /** 미송 하나에 줄 수량. {@code variantId}는 담지 않는다 — {@code backorderId}가 SKU 로 이어진다. */
    public record BackorderAllocationItem(Long backorderId, Integer allocateQty) {}
}
