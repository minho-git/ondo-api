package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.retailgateway.dto.RetailBackorderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 소매 미송 조회 (MUL-97).
 *
 * <p>상품({@link RetailGatewayListingService})과 달리 접을 게 없다 — 미송은 한 줄이
 * 그대로 한 줄이다. 그런데도 서비스를 두는 건 목록과 개수가 <b>같은 트랜잭션에서</b>
 * 나가야 해서다. 따로 나가면 그 사이에 도매가 배분을 확정했을 때 개수만 줄어
 * 소매가 없는 페이지를 그린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetailGatewayBackorderService {

    private final RetailGatewayBackorderQuery query;

    /** 그 소매처의 미송 대기 한 장. 오래된 순이다. */
    public Paged<RetailBackorderResponse> search(long retailerId, int page, int size) {
        List<RetailBackorderResponse> content = query.search(retailerId, page, size);
        return new Paged<>(content, query.count(retailerId));
    }
}
