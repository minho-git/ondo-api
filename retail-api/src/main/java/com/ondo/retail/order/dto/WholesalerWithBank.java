package com.ondo.retail.order.dto;

/**
 * 도매처 + 입금 계좌.
 *
 * <p>장바구니에선 계좌를 안 쓴다. 주문서에서 계좌 이체를 골랐을 때 처음 필요해서
 * 주문서 · 주문 상세에서만 내린다.
 *
 * <p>계좌 셋이 전부 null 이면 미등록이다. 그 도매처는 계좌 이체를 못 고른다 —
 * 프론트가 현금만 남긴다.
 */
public record WholesalerWithBank(
        Long id,
        String name,
        String storeBuilding,
        String storeUnit,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder) {
}
