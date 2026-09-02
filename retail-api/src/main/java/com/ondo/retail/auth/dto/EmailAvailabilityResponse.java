package com.ondo.retail.auth.dto;

/**
 * 이메일을 쓸 수 있는지.
 *
 * <p>Map 대신 record 로 두는 건 문서 때문이다. Map 이면 스웨거에 "문자열 → 불리언" 이라고만
 * 나와서 프론트가 키 이름을 알 수 없다.
 *
 * @param isAvailable true 면 아직 아무도 안 쓰는 이메일이다
 */
public record EmailAvailabilityResponse(boolean isAvailable) {
}
