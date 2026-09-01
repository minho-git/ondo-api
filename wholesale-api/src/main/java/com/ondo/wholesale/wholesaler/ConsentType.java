package com.ondo.wholesale.wholesaler;

/**
 * 동의 항목. 값 문자열은 DB CHECK 제약(wholesaler_consent_type_ck)과 1:1로 맞춘다.
 *
 * <p>가입 때 6종을 <b>전부</b> 받는다. 동의하지 않은 항목도 {@code agreed = false} 행으로
 * 남긴다 — "안 물어봤다"와 "물어봤는데 거절했다"는 다르고, 법적 증빙에서 그 차이가 중요하다.
 */
public enum ConsentType {

    /** 이용약관. 필수 */
    TERMS(true),
    /** 개인정보 수집·이용. 필수 */
    PRIVACY(true),
    /** 입력 정보가 사실임을 확인. 필수 */
    INFO_CONFIRM(true),

    MARKETING_SMS(false),
    MARKETING_EMAIL(false),
    MARKETING_PUSH(false);

    private final boolean required;

    ConsentType(boolean required) {
        this.required = required;
    }

    /** 필수 동의인가. false 로 오면 가입이 거절된다(REQUIRED_CONSENT_MISSING). */
    public boolean isRequired() {
        return required;
    }
}
