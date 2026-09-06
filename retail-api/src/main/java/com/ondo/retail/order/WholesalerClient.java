package com.ondo.retail.order;

import com.ondo.retail.order.dto.WholesalerWithBank;
import java.util.List;
import java.util.Map;

/**
 * 도매처 정보를 가져오는 통로 (MUL-98).
 *
 * <p>상품 조회가 주는 도매처는 id·상호뿐이다. 주문서에는 <b>입금 계좌</b>가 더 필요하다 —
 * 도매처마다 따로 입금하기 때문이다. 그건 도매 DB 에 있고 소매가 직접 못 읽는다.
 *
 * <p>{@code ListingClient}·{@code OrderClient} 와 같은 이유로 인터페이스가 여기 있다.
 */
public interface WholesalerClient {

    /** @return 찾은 것만 담긴다. 없는 id 는 키가 없다 */
    Map<Long, WholesalerWithBank> findAll(List<Long> wholesalerIds);
}
