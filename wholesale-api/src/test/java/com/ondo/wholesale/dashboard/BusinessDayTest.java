package com.ondo.wholesale.dashboard;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영업일 경계 계산 검증 (MUL-120). 영업일은 낮 12시(KST)에 시작한다 —
 * 동대문 도매는 저녁에 열어 새벽에 마치므로 자정 기준이면 하룻밤 주문이 이틀로 갈린다.
 */
class BusinessDayTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void 낮_12시_이후면_오늘_낮_12시가_영업일_시작이다() {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 10, 21, 42, 0, 0, KST);

        assertThat(BusinessDay.startFor(now))
                .isEqualTo(OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, KST));
    }

    @Test
    void 낮_12시_전이면_어제_낮_12시가_영업일_시작이다() {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 11, 2, 0, 0, 0, KST);

        assertThat(BusinessDay.startFor(now))
                .isEqualTo(OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, KST));
    }

    @Test
    void 정확히_낮_12시면_그날_낮_12시다() {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, KST);

        assertThat(BusinessDay.startFor(now))
                .isEqualTo(OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, KST));
    }

    @Test
    void 다른_오프셋_입력도_KST로_환산해_판정한다() {
        // UTC 03:30 = KST 12:30 — KST 로 보면 이미 오늘 영업일이 시작됐다
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 10, 3, 30, 0, 0, ZoneOffset.UTC);

        assertThat(BusinessDay.startFor(now))
                .isEqualTo(OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, KST));
    }
}
