package com.ondo.wholesale.retailgateway.dto;

/**
 * 소매 주문서에 필요한 도매처 정보 (MUL-98).
 *
 * <p>상품 목록이 주는 도매처({@code RetailListingSummaryResponse.Wholesaler})는 id·상호뿐이다.
 * 주문서에는 <b>입금 계좌</b>가 더 필요하다 — 도매처마다 따로 입금하기 때문이다.
 *
 * <p>계좌가 없을 수 있다. 도매처가 아직 등록을 안 한 경우로, 그때는 현금만 받는다.
 * 소매 화면이 결제 방법에서 계좌이체를 빼는 근거가 이 값이다.
 */
public record RetailWholesalerResponse(
        Long id,
        String name,
        String storeBuilding,
        String storeUnit,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder) {
}
