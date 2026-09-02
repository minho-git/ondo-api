package com.ondo.retail.order.dto;

/** 결제 조건. 도매처마다 고른다. 도매 명세의 expectedPaymentMethod 와 같은 값이다. */
public enum PaymentTerm {
    CASH,
    BANK_TRANSFER
}
