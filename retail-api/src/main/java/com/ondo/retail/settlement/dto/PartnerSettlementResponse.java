package com.ondo.retail.settlement.dto;

import java.time.LocalDate;

/**
 * 도매처별 미수 한 줄 (MUL-129) — 정산 · 미수 화면의 표와 요약 카드가 이걸 더한다.
 *
 * <p>잔액 · 연체는 전부 도매 원장에서 계산된 값이다. 부호는 <b>플러스 = 갚을 돈, 마이너스 = 선수금</b>.
 *
 * @param wholesalerId  도매처 id
 * @param name          도매처 상호
 * @param balance       미수 잔액. 음수면 선수금
 * @param overdue       연체. 출고마다 기한(출고일 + 외상기간, 당일은 아님)이 지났는데 남은 돈.
 *                      외상기간은 도매 거래처 설정 전까지 모두 1일이다
 * @param lastPaidAt    마지막 입금일. 입금 이력이 없으면 null
 * @param paidLast7Days 오늘 포함 최근 7일에 보낸 입금 합 — "이번 주 보낸 입금" 카드
 * @param bank          입금 계좌. 도매처가 등록 안 했으면 null
 */
public record PartnerSettlementResponse(
        Long wholesalerId,
        String name,
        long balance,
        Overdue overdue,
        LocalDate lastPaidAt,
        long paidLast7Days,
        Bank bank) {

    /** @param count 연체가 걸린 주문 수 · @param maxDays 가장 오래 밀린 것의 D+n (없으면 0) */
    public record Overdue(long amount, int count, int maxDays) {}

    /** @param holder 예금주 */
    public record Bank(String bankName, String accountNo, String holder) {}
}
