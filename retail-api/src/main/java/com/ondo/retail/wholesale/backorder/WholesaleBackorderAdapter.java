package com.ondo.retail.wholesale.backorder;

import static com.ondo.retail.wholesale.WholesaleCall.call;

import com.ondo.retail.backorder.BackorderClient;
import com.ondo.retail.backorder.dto.BackorderLine;
import com.ondo.retail.wholesale.backorder.dto.WholesaleBackorder;
import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 도매 미송 API 를 소매 말로 옮긴다 (MUL-97).
 *
 * <p>{@code MockBackorderData} 를 대신한다. 소매 컨트롤러가 내리는 응답 모양은 안 바뀌었다 —
 * {@link BackorderClient} 라는 문 뒤에서 목이 진짜로 갈렸을 뿐이다.
 *
 * <p>상품({@code WholesaleListingAdapter})과 달리 여기서 끝나지 않는다. 주문번호가 빠져 있어서
 * {@code BackorderService} 가 소매 DB 를 한 번 더 본다.
 */
@Component
@RequiredArgsConstructor
public class WholesaleBackorderAdapter implements BackorderClient {

    private final WholesaleBackorderApi api;

    @Override
    public Page<BackorderLine> findWaiting(Long retailerId, Pageable pageable) {
        WholesaleEnvelope<List<WholesaleBackorder>> response = call("미송 목록", () ->
                api.backorders(retailerId, pageable.getPageNumber(), pageable.getPageSize()));

        List<BackorderLine> content = response.data().stream()
                .map(WholesaleBackorderAdapter::toLine)
                .toList();

        // 전체 개수는 도매만 안다. meta 가 없으면 이 장이 전부인 것으로 본다
        long total = response.meta() != null ? response.meta().totalElements() : content.size();
        return new PageImpl<>(content, pageable, total);
    }

    // ── 도매 말 → 소매 말 ───────────────────────────────────────

    private static BackorderLine toLine(WholesaleBackorder source) {
        return new BackorderLine(
                source.backorderId(),
                source.retailOrderId(),
                source.orderedAt(),
                new BackorderLine.Wholesaler(source.wholesaler().id(), source.wholesaler().name()),
                source.listingId(),
                source.title(),
                source.colorName(),
                source.size(),
                source.qty(),
                source.expectedInboundDate(),
                source.expectedInboundReason());
    }
}
