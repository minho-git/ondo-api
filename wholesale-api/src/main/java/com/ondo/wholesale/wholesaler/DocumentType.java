package com.ondo.wholesale.wholesaler;

/**
 * 증빙 서류 종류. 값 문자열은 DB CHECK 제약(wholesaler_document_type_ck)과 1:1로 맞춘다.
 *
 * <p>같은 값을 approval_request.document_types 배열도 쓴다 — 심사에서 어느 서류가
 * 문제였는지 기록하는 자리다(MUL-71).
 */
public enum DocumentType {

    /** 사업자등록증. 필수 */
    BIZ_REG(true),
    /** 대표자 신분증. 필수 */
    CEO_ID(true),
    /** 매장 사진. 선택 */
    STORE_PHOTO(false);

    private final boolean required;

    DocumentType(boolean required) {
        this.required = required;
    }

    /** 필수 서류인가. 빠지면 가입이 거절된다(REQUIRED_DOCUMENT_MISSING). */
    public boolean isRequired() {
        return required;
    }
}
