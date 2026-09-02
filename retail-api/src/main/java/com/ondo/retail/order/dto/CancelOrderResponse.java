package com.ondo.retail.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 취소 결과. <b>일부만 취소돼도 200 이다</b> — 접수와 같은 모양이다.
 *
 * <p>NEW 인 것만 취소된다. 확정되면 못 한다 — 이미 미수가 발생했고 배분·미송이 달려 있다.
 *
 * @param results 도매처별 결과. 성공과 실패가 섞여 온다
 */
public record CancelOrderResponse(List<Result> results) {

    /**
     * @param wholesaleOrderId 어느 도매처 건인지
     * @param isCancelled      취소됐는지
     * @param reason           안 된 이유 코드. 성공이면 null
     * @param message          화면에 그대로 쓸 문구. 성공이면 null
     */
    @Schema(name = "CancelOrderResult")
    public record Result(Long wholesaleOrderId, boolean isCancelled, String reason, String message) {
    }
}
