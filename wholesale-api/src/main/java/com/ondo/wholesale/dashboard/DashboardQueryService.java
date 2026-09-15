package com.ondo.wholesale.dashboard;

import com.ondo.wholesale.dashboard.dto.DashboardSummaryResponse;
import org.springframework.stereotype.Service;

/**
 * 대시보드 summary 조립 (MUL-120) — 로그인한 도매처 기준, 파라미터 없음.
 */
@Service
public class DashboardQueryService {

    public DashboardSummaryResponse summary(Long wholesalerId) {
        throw new UnsupportedOperationException("MUL-120 집계 조립에서 구현한다");
    }
}
