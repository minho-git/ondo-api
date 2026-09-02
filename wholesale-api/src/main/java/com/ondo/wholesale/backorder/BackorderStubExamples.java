package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.AllocationBatchResponse;
import com.ondo.wholesale.backorder.dto.AllocationPackingResponse;
import com.ondo.wholesale.backorder.dto.BackorderListResponse;
import com.ondo.wholesale.backorder.dto.BackorderResponse;
import com.ondo.wholesale.backorder.dto.BackorderSkuResponse;
import com.ondo.wholesale.backorder.dto.BackorderStatsResponse;
import com.ondo.wholesale.backorder.dto.ExpectedInboundResponse;
import com.ondo.wholesale.order.PackingStatus;
import com.ondo.wholesale.order.dto.PackingItemResponse;
import com.ondo.wholesale.product.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 계약 스텁 example — api-lite/05_미송 문서의 Response 예시 그대로. 실구현이 서비스 호출로 교체한다. */
final class BackorderStubExamples {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private BackorderStubExamples() {
    }

    static List<BackorderSkuResponse> backorderSkus() {
        return List.of(new BackorderSkuResponse(
                90231L, 5012L, 18, 1, "오버핏 코튼 티셔츠", "블랙", Size.M, 90, 35, null));
    }

    static BackorderListResponse backordersOfSku() {
        BackorderResponse row = new BackorderResponse(
                6101L, 5601L, 1001, 88201L,
                OffsetDateTime.of(2024, 7, 15, 9, 30, 0, 0, KST),
                OffsetDateTime.of(2024, 7, 15, 14, 2, 0, 0, KST),
                34, 3311L, "다올몰", 12, 12, 15000);
        BackorderStatsResponse stats = new BackorderStatsResponse(
                90231L, 18, 1, 90, 10, 10, 35, null, null,
                OffsetDateTime.of(2024, 7, 15, 9, 30, 0, 0, KST),
                OffsetDateTime.of(2024, 8, 5, 14, 35, 0, 0, KST),
                1520000);
        return new BackorderListResponse(List.of(row), stats);
    }

    static AllocationBatchResponse allocationBatch() {
        AllocationPackingResponse packing = new AllocationPackingResponse(
                7710L, 5601L, 1001, PackingStatus.READY, null, true,
                OffsetDateTime.of(2024, 8, 18, 11, 20, 0, 0, KST),
                List.of(new PackingItemResponse(
                        91101L, 88201L, 90231L, 18, 1, "오버핏 코튼 티셔츠", "블랙", Size.M, 12)));
        return new AllocationBatchResponse(
                4401L,
                OffsetDateTime.of(2024, 8, 18, 11, 20, 0, 0, KST),
                List.of(packing),
                List.of(6101L, 6102L, 6103L));
    }

    static ExpectedInboundResponse expectedInbound() {
        return new ExpectedInboundResponse(
                90231L, 18, 1, LocalDate.of(2024, 7, 15), "공장 생산 일정이 3일 밀려요.");
    }
}
