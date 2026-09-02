package com.ondo.retail.order.dto;

/**
 * 도매처 + 입금 계좌.
 *
 * <p>장바구니에선 계좌를 안 쓴다. 주문서에서 계좌 이체를 골랐을 때 처음 필요해서
 * 주문서 · 주문 상세에서만 내린다.
 *
 * <p>계좌 셋이 전부 null 이면 미등록이다. 그 도매처는 계좌 이체를 못 고른다 —
 * 프론트가 현금만 남긴다.
 *
 * @param id                도매처 id
 * @param name              도매처 상호
 * @param storeBuilding     매장 건물. 청평화패션몰 같은 것
 * @param storeUnit         매장 호수. 2층 24호 같은 것
 * @param bankName          입금 은행. 미등록이면 null
 * @param bankAccountNo     계좌번호. 미등록이면 null
 * @param bankAccountHolder 예금주. 미등록이면 null
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
