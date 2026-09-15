package com.ondo.wholesale.dashboard;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 대시보드의 "오늘" = 영업일. 낮 12시(KST)에 시작한다 (MUL-120).
 *
 * <p>동대문 도매는 저녁에 열어 새벽에 마치므로 자정을 경계로 하면 하룻밤 주문이
 * 이틀로 갈린다. 구간은 {@code [startFor(now), 다음 영업일 시작)} 반개구간으로 쓴다 —
 * {@link com.ondo.wholesale.common.time.KstDays}의 자정 반개구간과 같은 꼴.
 */
public final class BusinessDay {

    private BusinessDay() {}

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPENING = LocalTime.NOON;

    /** 지금이 속한 영업일의 시작 시각 — KST 낮 12시 이후면 오늘 12:00, 이전이면 어제 12:00. */
    public static OffsetDateTime startFor(OffsetDateTime now) {
        ZonedDateTime kstNow = now.atZoneSameInstant(KST);
        LocalDate day = kstNow.toLocalTime().isBefore(OPENING)
                ? kstNow.toLocalDate().minusDays(1)
                : kstNow.toLocalDate();
        return day.atTime(OPENING).atZone(KST).toOffsetDateTime();
    }
}
