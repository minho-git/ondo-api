package com.ondo.retail.wholesale.order;

import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderCreateRequest;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderCreated;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderView;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
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

    /**
     * 주문 조회. 주문서 하나에 도매처 수만큼 온다.
     *
     * <p>{@code retailerId} 를 같이 싣는다 — 주문서 id 만 믿으면 남의 주문을 읽을 수 있어
     * 도매가 소매처로 한 번 더 거른다. 미송(MUL-97)과 같은 방식이다.
     */
    @GetExchange("/api/retail-gateway/orders")
    WholesaleEnvelope<List<WholesaleOrderView>> orders(@RequestParam Long retailerId,
                                                       @RequestParam List<Long> retailOrderIds);
}
