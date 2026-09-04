package com.ondo.retail.backorder;

import com.ondo.retail.backorder.dto.BackorderLine;
import com.ondo.retail.backorder.dto.BackorderResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 미송 대기 현황 (MUL-97).
 *
 * <p>줄은 도매에서 오고 주문번호는 소매 DB 에서 온다. 둘을 여기서 합친다.
 *
 * <p><b>{@code @Transactional} 이 없다.</b> 도매 호출이 트랜잭션 안에 들어가면 응답을
 * 기다리는 내내 DB 커넥션을 붙들고 있게 된다. 도매가 느려지면 소매 커넥션 풀이
 * 말라서 로그인까지 같이 죽는다. 읽기 한 방이라 트랜잭션으로 묶을 이유도 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackorderService {

    private final BackorderClient client;
    private final OrderNoQuery orderNoQuery;

    /**
     * 그 소매처의 미송 대기 한 장. 오래된 순이다.
     *
     * @param retailerId 세션에서 꺼낸 값이어야 한다
     */
    public Page<BackorderResponse> waiting(Long retailerId, Pageable pageable) {
        Page<BackorderLine> lines = client.findWaiting(retailerId, pageable);

        List<Long> orderIds = lines.getContent().stream().map(BackorderLine::orderId).distinct().toList();
        Map<Long, String> orderNos = orderNoQuery.byOrderIds(retailerId, orderIds);

        return lines.map(line -> toResponse(line, orderNos.get(line.orderId())));
    }

    /**
     * 주문번호를 못 찾아도 줄을 버리지 않는다.
     *
     * <p>버리면 소매처가 기다리는 물건 하나가 화면에서 소리 없이 사라진다. 번호만 비우고
     * 로그를 남긴다 — 이게 뜬다는 건 도매와 소매의 주문서가 어긋났다는 뜻이라
     * 조용히 넘어가면 안 되는 일이다.
     */
    private BackorderResponse toResponse(BackorderLine line, String orderNo) {
        if (orderNo == null) {
            log.warn("미송에 걸린 주문서를 소매에서 못 찾았다. backorderId={} orderId={}",
                    line.backorderId(), line.orderId());
        }
        return new BackorderResponse(
                line.backorderId(),
                line.orderId(),
                orderNo,
                line.orderedAt(),
                new BackorderResponse.Wholesaler(line.wholesaler().id(), line.wholesaler().name()),
                line.listingId(),
                line.title(),
                line.colorName(),
                line.size(),
                line.qty(),
                line.expectedInboundDate(),
                line.expectedInboundReason());
    }
}
