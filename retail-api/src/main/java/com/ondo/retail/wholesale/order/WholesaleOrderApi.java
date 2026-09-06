package com.ondo.retail.wholesale.order;

import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderCreateRequest;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderCreated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 도매 소매접점 주문 API 의 계약 (MUL-98).
 *
 * <p>구현체가 없다. 앱이 뜰 때 스프링이 만들어 끼운다 — 상품·미송과 같은 그룹이라
 * 주소·타임아웃·게이트웨이 시크릿이 그대로 붙는다 ({@code WholesaleHttpConfig}).
 */
@HttpExchange
public interface WholesaleOrderApi {

    /** 도매처 한 곳에 주문을 넣는다. 중복이면 도매가 409 를 준다. */
    @PostExchange("/api/retail-gateway/orders")
    WholesaleEnvelope<WholesaleOrderCreated> create(@RequestBody WholesaleOrderCreateRequest request);
}
