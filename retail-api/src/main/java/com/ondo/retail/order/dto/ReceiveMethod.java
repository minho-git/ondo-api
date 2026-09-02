package com.ondo.retail.order.dto;

/** 수령 방법. AGENT 면 사입삼촌 정보가 필수다. 도매 명세의 receiveBy 와 같은 값이다. */
public enum ReceiveMethod {
    RETAILER,
    AGENT
}
