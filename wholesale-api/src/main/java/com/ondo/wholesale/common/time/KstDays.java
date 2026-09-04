package com.ondo.wholesale.common.time;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 한국 날짜의 하루 경계 — 기간 필터가 전제하는 [시작, 다음날 시작) 반개구간의 재료.
 *
 * <p>화면·계약이 한국 날짜를 전제하므로 timestamptz 컬럼에 날짜 필터를 걸 때는
 * 항상 이 경계를 쓴다 (상품 등록일·주문일 공용).
 */
public final class KstDays {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstDays() {
    }

    /** 그 날의 KST 00:00. */
    public static OffsetDateTime start(LocalDate day) {
        return day.atStartOfDay(KST).toOffsetDateTime();
    }

    /** 다음 날의 KST 00:00 — 반개구간의 열린 끝. */
    public static OffsetDateTime startOfNext(LocalDate day) {
        return day.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
    }
}
