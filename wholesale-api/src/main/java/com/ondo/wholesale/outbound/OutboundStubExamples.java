package com.ondo.wholesale.outbound;

import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.ReceiveBy;
import com.ondo.wholesale.order.dto.PackingItemResponse;
import com.ondo.wholesale.outbound.dto.OutboundCreatedResponse;
import com.ondo.wholesale.outbound.dto.OutboundDetailResponse;
import com.ondo.wholesale.outbound.dto.OutboundItemResponse;
import com.ondo.wholesale.outbound.dto.OutboundRetailerResponse;
import com.ondo.wholesale.outbound.dto.OutboundSummaryResponse;
import com.ondo.wholesale.outbound.dto.PackingItemRowResponse;
import com.ondo.wholesale.outbound.dto.PackingRetailerResponse;
import com.ondo.wholesale.outbound.dto.StatementResponse;
import com.ondo.wholesale.product.domain.Size;

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

    static OutboundCreatedResponse createdOutbound() {
        return new OutboundCreatedResponse(
                8801L, 1, 3307L, "부산 상사", null, null,
                OffsetDateTime.of(2026, 8, 12, 14, 20, 0, 0, KST), 30,
                List.of(new OutboundCreatedResponse.Packing(
                        7701L, 5601L, 1001, PackingStatus.PACKED,
                        List.of(new PackingItemResponse(
                                91101L, 88201L, 90231L, 18, 1,
                                "오버핏 코튼 티셔츠", "블랙", Size.M, 12)))));
    }

    /** 출고 전(포장 완료) 상세 — isShippable true, shippedAt·statementNumber null. */
    static OutboundDetailResponse detailBeforeShip() {
        return detail(null, null, true);
    }

    /** 출고 확정 직후 상세 — shippedAt·statementNumber 채워짐. */
    static OutboundDetailResponse detailAfterShip() {
        return detail(OffsetDateTime.of(2026, 8, 14, 10, 30, 0, 0, KST), 1, false);
    }

    private static OutboundDetailResponse detail(OffsetDateTime shippedAt, Integer statementNumber,
                                                 boolean shippable) {
        return new OutboundDetailResponse(
                8801L, 1, 3307L, "RT-007", "부산 상사",
                OffsetDateTime.of(2026, 8, 12, 14, 20, 0, 0, KST),
                shippedAt, statementNumber, shippable, 30,
                List.of(
                        new OutboundItemResponse(90231L, 18, 1, "오버핏 코튼 티셔츠", "블랙", Size.M, 12),
                        new OutboundItemResponse(90232L, 19, 2, "린넨 플로 셔츠", "베이지", Size.L, 8),
                        new OutboundItemResponse(90233L, 20, 3, "헤비웨이트 후드", "차콜", Size.XL, 10)),
                List.of(
                        new OutboundDetailResponse.PackingRef(7701L, 5601L, 1001),
                        new OutboundDetailResponse.PackingRef(7702L, 5602L, 1002),
                        new OutboundDetailResponse.PackingRef(7703L, 5603L, 1003)));
    }

    static StatementResponse statement() {
        return new StatementResponse(
                1, 1, OffsetDateTime.of(2026, 8, 14, 10, 30, 0, 0, KST),
                "도도도매", "RT-007", "부산상사", "부산 부산진구 서전로 8 1801호",
                ReceiveBy.RETAILER, 30,
                List.of(
                        new StatementResponse.Item("오버핏 코튼 티셔츠", "블랙", "M", 12),
                        new StatementResponse.Item("린넨 플로 셔츠", "베이지", "L", 8),
                        new StatementResponse.Item("헤비웨이트 후드", "차콜", "XL", 10)));
    }
}
