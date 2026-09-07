package com.ondo.retail.wholesale.wholesaler.dto;

/**
 * 도매가 내려주는 도매처 정보 그대로 (MUL-98).
 *
 * <p>계좌가 null 이면 아직 등록을 안 한 도매처다. 소매 화면이 계좌이체를 못 고르게
 * 막는 근거가 이 값이다.
 */
public record WholesaleWholesaler(
        Long id,
        String name,
        String storeBuilding,
        String storeUnit,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder) {
}
