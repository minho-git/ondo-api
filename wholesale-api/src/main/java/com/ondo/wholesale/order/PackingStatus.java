package com.ondo.wholesale.order;

/** 포장 카드 상태. PACKED 는 출고 묶음 해제(unpack) 없이는 취소할 수 없다. */
public enum PackingStatus {
    READY, PACKED
}
