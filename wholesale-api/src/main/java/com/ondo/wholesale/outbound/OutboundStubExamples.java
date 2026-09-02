package com.ondo.wholesale.outbound;

import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.outbound.dto.OutboundRetailerResponse;
import com.ondo.wholesale.outbound.dto.OutboundSummaryResponse;
import com.ondo.wholesale.outbound.dto.PackingItemRowResponse;
import com.ondo.wholesale.outbound.dto.PackingRetailerResponse;
import com.ondo.wholesale.product.Size;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 계약 스텁 example — api-lite/06_출고 문서의 Response 예시 그대로. 실구현이 서비스 호출로 교체한다. */
final class OutboundStubExamples {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private OutboundStubExamples() {
    }

    static List<PackingRetailerResponse> packingRetailers() {
        return List.of(new PackingRetailerResponse(3307L, "RT-007", "부산 상사", 6, 55));
    }

    static List<PackingItemRowResponse> packingItems() {
        return List.of(new PackingItemRowResponse(
                91101L, 7701L, 5601L, 1001, 90231L, 18, 1,
                "오버핏 코튼 티셔츠", "블랙", Size.M, ReceiveBy.RETAILER,
                OffsetDateTime.of(2026, 8, 12, 9, 14, 0, 0, KST), 12));
    }

    static List<OutboundRetailerResponse> outboundRetailers() {
        return List.of(new OutboundRetailerResponse(
                3307L, "RT-007", "부산 상사", 3, 66,
                OffsetDateTime.of(2026, 8, 12, 14, 20, 0, 0, KST), null));
    }

    static List<OutboundSummaryResponse> outboundSummaries() {
        return List.of(new OutboundSummaryResponse(
                8801L, 1, "오버핏 코튼 티셔츠", 2, ReceiveBy.RETAILER,
                OffsetDateTime.of(2026, 8, 12, 14, 20, 0, 0, KST), null, null, 30));
    }
}
