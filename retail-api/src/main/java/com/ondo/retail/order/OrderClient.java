package com.ondo.retail.order;

import com.ondo.retail.order.dto.WholesaleOrderCommand;
import com.ondo.retail.order.dto.WholesaleOrderReceipt;

/**
 * 도매에 주문을 넣는 통로.
 *
 * <p>주문은 도매 DB 에 쓰는 일이라 소매가 직접 못 한다. 도매의
 * {@code POST /api/retail-gateway/orders} 를 부르는
 * {@code com.ondo.retail.wholesale.order.WholesaleOrderAdapter} 가 구현한다 (MUL-98).
 *
 * <p>{@code ListingClient}·{@code BackorderClient} 와 같은 이유로 인터페이스가 여기 있다 —
 * 이 패키지는 상대가 누구인지, HTTP 인지를 몰라야 한다.
 *
 * <p><b>실패를 예외로 던지지 않는다.</b> 도매처 하나가 거절해도 나머지는 접수돼야 하고,
 * 거절 사유가 화면에 그대로 뜬다. 예외로 만들면 부르는 쪽이 try/catch 로 결과를
 * 조립하게 되는데 그건 결과지 사고가 아니다.
 */
public interface OrderClient {

    WholesaleOrderReceipt place(WholesaleOrderCommand command);
}
