package com.ondo.retail.wholesale.settlement;

import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.settlement.dto.WholesaleSettlementLedgerEntry;
import com.ondo.retail.wholesale.settlement.dto.WholesaleSettlementSummary;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 도매 소매접점 정산 API 의 계약 (MUL-129). 구현체는 {@code WholesaleHttpConfig} 가 만들어 끼운다 —
 * 미송과 같은 그룹이라 주소 · 타임아웃 · 게이트웨이 시크릿 헤더가 그대로 붙는다.
 */
@HttpExchange
public interface WholesaleSettlementApi {

    @GetExchange("/api/retail-gateway/settlements")
    WholesaleEnvelope<List<WholesaleSettlementSummary>> summaries(@RequestParam Long retailerId);

    @GetExchange("/api/retail-gateway/settlements/ledger")
    WholesaleEnvelope<List<WholesaleSettlementLedgerEntry>> ledger(@RequestParam Long retailerId,
                                                                   @RequestParam Long wholesalerId);
}
