package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import com.ondo.wholesale.security.WholesalePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 API (MUL-120) — 원본 계약: FE 문서 "도매 ERP 대시보드 — 기능 명세 및 BE 요청 사항".
 */
@Tag(name = "09 대시보드")
@RestController
@RequestMapping("/api/wholesale")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    @Operation(summary = "대시보드 summary (집계 한 방)", description = """
            로그인한 도매처 기준 대시보드 1차 화면의 숫자를 한 번에 내린다 — 파라미터 없음.
            30초 polling 용이라 목록 API 여러 개를 합산하지 않아도 된다.

            `now`는 서버 시각 — 경과 시간은 이 값 기준으로 계산한다.
            "오늘"(`today`)의 경계는 자정이 아니라 영업일 시작(KST 낮 12시)이다.
            `outbound.staleCount`는 이번 영업일 시작 전에 포장했는데 아직 출고
            확정하지 않은 봉투 수, `today.cancelled`는 오늘 접수분 중 취소 건수다.""")
    @GetMapping("/dashboard/summary")
    public DashboardSummaryResponse summary(@AuthenticationPrincipal WholesalePrincipal principal) {
        return dashboardQueryService.summary(principal.wholesalerId());
    }
}
