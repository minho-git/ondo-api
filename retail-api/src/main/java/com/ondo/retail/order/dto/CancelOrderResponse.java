package com.ondo.retail.order.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 취소 결과. <b>일부만 취소돼도 200 이다</b> — 접수와 같은 모양이다.
 *
 * <p>NEW 인 것만 취소된다. 확정되면 못 한다 — 이미 미수가 발생했고 배분·미송이 달려 있다.
 */
public record CancelOrderResponse(List<Result> results) {

    @Schema(name = "CancelOrderResult")

    public record Result(Long wholesaleOrderId, boolean isCancelled, String reason, String message) {
    }
}
