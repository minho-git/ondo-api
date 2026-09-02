package com.ondo.wholesale.outbound.dto;

import com.ondo.wholesale.order.ReceiveBy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 장끼(거래명세서) — 다운로드·인쇄의 원본 데이터. 출고 확정 전에는 404.
 *
 * <p>{@code statementNumber}는 날짜별로 1부터 다시 시작하는 정수라 {@code shippedAt} 없이는
 * 장끼를 특정할 수 없다 — 표시 코드(JG-20260814-001) 조립은 프론트. 금액 컬럼은 화면에 없어 안 담는다.
 */
public record StatementResponse(
        Integer statementNumber,
        Integer outboundNumber,
        OffsetDateTime shippedAt,
        String sellerName,
        String retailerCode,
        String retailerName,
        String deliveryAddress,
        ReceiveBy receiveBy,
        int totalQty,
        List<Item> items
) {

    /** 장끼 품목 한 줄. 옵션 한 칸(네이비 / M) 조립은 프론트. */
    public record Item(String productName, String color, String size, int qty) {}
}
