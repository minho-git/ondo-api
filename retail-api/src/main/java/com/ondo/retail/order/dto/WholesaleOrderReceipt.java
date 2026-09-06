package com.ondo.retail.order.dto;

/**
 * 도매처 한 곳의 접수 결과 (MUL-98).
 *
 * <p>성공과 실패를 <b>한 타입으로</b> 표현한다. 부분 성공이 정상 결과라서다 —
 * 넷 중 셋만 받아진 것은 실패가 아니라 그냥 그런 결과다.
 *
 * @param accepted         받아졌는지
 * @param wholesaleOrderId 도매쪽 주문 id. 거절이면 null
 * @param orderNumber      도매처별 연번. 거절이면 null
 * @param amount           이 도매처 금액. 거절이면 null
 * @param reason           거절 코드. 도매가 준 것을 그대로 옮긴다. 성공이면 null
 * @param message          화면에 그대로 쓸 문구. 성공이면 null
 */
public record WholesaleOrderReceipt(
        boolean accepted,
        Long wholesaleOrderId,
        Integer orderNumber,
        Integer amount,
        String reason,
        String message) {

    public static WholesaleOrderReceipt accepted(Long orderId, Integer orderNumber, Integer amount) {
        return new WholesaleOrderReceipt(true, orderId, orderNumber, amount, null, null);
    }

    public static WholesaleOrderReceipt rejected(String reason, String message) {
        return new WholesaleOrderReceipt(false, null, null, null, reason, message);
    }
}
