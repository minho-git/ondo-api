package com.ondo.retail.backorder;

import com.ondo.retail.backorder.dto.BackorderLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 도매에서 미송 대기 현황을 가져오는 통로.
 *
 * <p>미송은 도매가 만들고 도매가 푼다. 소매는 읽기만 한다. DB 가 갈라져 있어서
 * 소매가 직접 못 읽고, 도매의 {@code /api/retail-gateway/backorders} 를 부르는
 * {@code com.ondo.retail.wholesale.backorder.WholesaleBackorderAdapter} 가 구현한다 (MUL-97).
 *
 * <p><b>인터페이스가 여기 남아 있는 이유</b> — 이 패키지는 상대가 누구인지, HTTP 인지를
 * 몰라야 한다. {@code ListingClient} 와 같은 이유다.
 */
public interface BackorderClient {

    /**
     * 그 소매처가 아직 못 받은 것. 오래된 순이다 — 미송은 FIFO 로 풀린다.
     *
     * @param retailerId 세션에서 꺼낸 값이어야 한다. 요청에 담긴 값을 그대로 넘기면
     *                   남의 미송을 볼 수 있다
     */
    Page<BackorderLine> findWaiting(Long retailerId, Pageable pageable);
}
