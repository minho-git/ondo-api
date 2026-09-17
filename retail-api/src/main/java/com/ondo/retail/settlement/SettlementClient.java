package com.ondo.retail.settlement;

import com.ondo.retail.settlement.dto.LedgerLine;
import com.ondo.retail.settlement.dto.SettlementSummaryLine;

import java.util.List;

/**
 * 도매에서 정산을 가져오는 통로 (MUL-129). 돈 기록은 도매 DB 에 있어 소매는 읽기만 한다.
 * {@code com.ondo.retail.wholesale.settlement.WholesaleSettlementAdapter} 가 구현한다 — {@code BackorderClient} 와 같은 이유로
 * 이 패키지는 상대가 HTTP 인지 모른다.
 */
public interface SettlementClient {

    /** @param retailerId 세션에서 꺼낸 값이어야 한다. 요청 값을 넘기면 남의 정산을 볼 수 있다 */
    List<SettlementSummaryLine> summaries(Long retailerId);

    /** 오래된 순. 거래 관계가 없는 도매처면 빈 목록. */
    List<LedgerLine> ledger(Long retailerId, Long wholesalerId);
}
