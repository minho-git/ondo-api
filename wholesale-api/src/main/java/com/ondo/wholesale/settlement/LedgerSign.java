package com.ondo.wholesale.settlement;

/**
 * 원장 부호를 화면 계약으로 옮기는 유일한 자리 (MUL-124).
 *
 * <p>저장은 <b>플러스 = 소매처가 갚을 돈이 늘었다</b>(출고 +, 입금 −). 도매 화면 계약은 거래처 계정
 * 잔액 관점이라 반대다 — 판매 −, 입금 +, 잔액 음수 = 소매처 채무. 두 번 뒤집히면 빚이 선수금으로
 * 보이므로 뒤집기는 여기서만 한다.
 */
public final class LedgerSign {

    private LedgerSign() {
    }

    /** 저장된 잔액·증감 → 도매 화면 계약 값. */
    public static int toWholesaleScreen(long stored) {
        return Math.toIntExact(-stored);
    }
}
