package com.ondo.wholesale.retailgateway.dto;

import java.time.LocalDate;

/**
 * 소매처 하나가 보는 도매처별 정산 한 줄 (MUL-129).
 *
 * <p>부호는 <b>소매 화면 기준</b> — {@code balance} 플러스 = 소매처가 갚을 돈, 마이너스 = 선수금.
 * 도매 저장과 같아서 뒤집지 않는다(도매 화면만 반대다).
 *
 * @param lastPaidAt     마지막 입금일(KST). 취소된 입금은 뺀다. 없으면 null
 * @param paidLast7Days  오늘 포함 최근 7일(KST) 입금 합 — 소매 "이번 주 보낸 입금" 카드
 * @param bankName       입금 계좌. 도매처가 등록 안 했으면 셋 다 null
 */
public record RetailSettlementSummaryResponse(
        Long wholesalerId,
        String wholesalerName,
        long balance,
        Overdue overdue,
        LocalDate lastPaidAt,
        long paidLast7Days,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder) {

    /** @param count 연체가 걸린 주문 수 · @param maxDays 가장 오래 밀린 것의 D+n */
    public record Overdue(long amount, int count, int maxDays) {}
}
