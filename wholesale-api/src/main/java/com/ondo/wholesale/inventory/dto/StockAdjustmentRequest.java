package com.ondo.wholesale.inventory.dto;

/**
 * 재고 조정 요청. 절대값이 아니라 부호 포함 증감({@code qtyChange})을 받는다 —
 * 절대값은 화면과 서버 사이에 낀 다른 트랜잭션의 변동을 덮어쓴다. {@code 0}은 거절.
 */
public record StockAdjustmentRequest(Integer qtyChange) {}
