package com.ondo.retail.wholesale.wholesaler;

import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.wholesaler.dto.WholesaleWholesaler;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 도매 소매접점 도매처 API 의 계약 (MUL-98).
 *
 * <p>구현체가 없다. 앱이 뜰 때 스프링이 만들어 끼운다 — 상품·미송·주문과 같은 그룹이라
 * 주소·타임아웃·게이트웨이 시크릿이 그대로 붙는다.
 */
@HttpExchange
public interface WholesaleWholesalerApi {

    @GetExchange("/api/retail-gateway/wholesalers")
    WholesaleEnvelope<List<WholesaleWholesaler>> wholesalers(@RequestParam List<Long> ids);
}
