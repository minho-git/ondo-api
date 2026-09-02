package com.ondo.wholesale.order;

/** 결제 수단 (D-090). 주문의 expectedPaymentMethod 는 "주문 시점의 약속"이라 실제 입금 수단과 어긋날 수 있다. */
public enum PaymentMethod {
    CASH, BANK_TRANSFER
}
