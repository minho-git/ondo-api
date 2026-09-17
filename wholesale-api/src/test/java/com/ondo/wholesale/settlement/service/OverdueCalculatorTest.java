package com.ondo.wholesale.settlement.service;

import com.ondo.wholesale.settlement.service.OverdueCalculator.Overdue;
import com.ondo.wholesale.settlement.service.OverdueCalculator.Shipment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 연체 계산 규칙 (MUL-129) — 외상기간 1일, 기한 당일은 연체 아님, 붙은 돈은 오래된 출고부터. */
class OverdueCalculatorTest {

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 17);

    @Test
    void 기한_당일은_연체가_아니고_다음_날부터_연체다() {
        // 9/16 출고 → 기한 9/17(오늘) → 아직 아님
        assertThat(OverdueCalculator.of(List.of(출고(1, 9, 16, 30000, 1)), Map.of(), 오늘))
                .isEqualTo(Overdue.NONE);
        // 9/15 출고 → 기한 9/16 → 오늘 D+1
        assertThat(OverdueCalculator.of(List.of(출고(1, 9, 15, 30000, 1)), Map.of(), 오늘))
                .isEqualTo(new Overdue(30000, 1, 1));
    }

    @Test
    void 붙은_돈은_그_주문의_오래된_출고부터_갚는다() {
        // 바지 주문: 9/1 20만 · 9/10 20만 출고, 25만 받음 → 9/1분 완납 · 9/10분 15만 남음(기한 9/11 → D+6)
        Overdue overdue = OverdueCalculator.of(
                List.of(출고(7, 9, 10, 200000, 2), 출고(7, 9, 1, 200000, 1)),
                Map.of(7L, 250000L), 오늘);

        assertThat(overdue).isEqualTo(new Overdue(150000, 1, 6));
    }

    @Test
    void 다_갚은_주문은_빠지고_연체_주문_수와_최장_일수를_센다() {
        Overdue overdue = OverdueCalculator.of(
                List.of(출고(1, 9, 1, 10000, 1), 출고(2, 9, 12, 5000, 2), 출고(3, 9, 5, 8000, 3)),
                Map.of(1L, 10000L), 오늘);

        // 주문 1 완납 · 주문 2 기한 9/13 → D+4 · 주문 3 기한 9/6 → D+11
        assertThat(overdue).isEqualTo(new Overdue(13000, 2, 11));
    }

    private static Shipment 출고(long orderId, int month, int day, long amount, long sequence) {
        return new Shipment(orderId, LocalDate.of(2026, month, day), amount, sequence);
    }
}
