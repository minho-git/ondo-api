package com.ondo.retail.wholesale.backorder;

import com.ondo.retail.wholesale.backorder.dto.WholesaleBackorder;
import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 도매 소매접점 미송 API 의 계약 (MUL-97).
 *
 * <p><b>구현체가 없다.</b> 앱이 뜰 때 스프링이 이 인터페이스를 보고 만들어 끼운다
 * ({@code WholesaleHttpConfig} 의 {@code @ImportHttpServices}). 상품과 같은 그룹이라
 * baseUrl · 타임아웃 · 게이트웨이 시크릿 헤더가 그대로 붙는다.
 */
@HttpExchange
public interface WholesaleBackorderApi {

    /**
     * 미송 대기 목록. 정렬은 도매가 오래된 순으로 고정한다 — 축을 고를 수 없다.
     *
     * <p>{@code retailerId} 를 싣는 건 미송이 소매처별로 잘린 데이터라서다. 상품에는
     * 없던 파라미터다 — 상품은 누가 보든 같은 걸 본다.
     */
    @GetExchange("/api/retail-gateway/backorders")
    WholesaleEnvelope<List<WholesaleBackorder>> backorders(
            @RequestParam Long retailerId,
            @RequestParam int page,
            @RequestParam int size);
}
