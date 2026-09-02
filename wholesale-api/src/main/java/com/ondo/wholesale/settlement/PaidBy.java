package com.ondo.wholesale.settlement;

/**
 * 결제 주체 — 누구 손으로 왔나. 수단({@code method})과 다른 축이다:
 * 사입삼촌이 계좌이체하면 AGENT + BANK_TRANSFER.
 */
public enum PaidBy {
    RETAILER, AGENT
}
