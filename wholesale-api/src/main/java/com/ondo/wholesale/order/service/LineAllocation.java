package com.ondo.wholesale.order.service;

/** 검증을 통과한 라인 하나의 배분 지시 — {@link AllocationValidator}가 만들고 {@link AllocationWriter}가 쓴다. */
public record LineAllocation(Long orderItemId, int allocateQty) {
}
