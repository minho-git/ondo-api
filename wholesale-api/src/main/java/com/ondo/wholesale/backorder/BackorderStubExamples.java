package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.ExpectedInboundResponse;

import java.time.LocalDate;

/** 계약 스텁 example — api-lite/05_미송 문서의 Response 예시 그대로. 실구현이 서비스 호출로 교체한다. */
final class BackorderStubExamples {

    private BackorderStubExamples() {
    }

    static ExpectedInboundResponse expectedInbound() {
        return new ExpectedInboundResponse(
                90231L, 18, 1, LocalDate.of(2024, 7, 15), "공장 생산 일정이 3일 밀려요.");
    }
}
