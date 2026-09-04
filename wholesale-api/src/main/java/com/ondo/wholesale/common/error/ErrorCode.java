package com.ondo.wholesale.common.error;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. {@code code} 문자열은 enum 이름을 그대로 쓴다.
 *
 * <p>티켓(MUL-66) 명시 4종 + 방어용 2종({@code ACCESS_DENIED}, {@code INTERNAL_ERROR})
 * + 가입(MUL-68) 5종.
 *
 * <p>가입 5종은 전부 400 이다. 소매(MUL-78)는 이메일 중복을 409 로 냈지만,
 * 도매 티켓이 400 을 명시했으므로 티켓을 따른다.
 */
public enum ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    NOT_APPROVED(HttpStatus.FORBIDDEN, "승인 대기 중인 계정입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ── 가입 (MUL-68) ──
    // 형식이 아니라 정책을 어겼을 때 쓴다. 형식 위반은 위의 VALIDATION_FAILED 로 뭉뚱그린다.
    // 클라이언트가 화면에서 다르게 처리해야 하는 것들이라 코드를 따로 뒀다.
    EMAIL_DUPLICATED(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),
    BIZ_REG_NO_DUPLICATED(HttpStatus.BAD_REQUEST, "이미 가입된 사업자등록번호입니다."),
    PASSWORD_POLICY_VIOLATED(HttpStatus.BAD_REQUEST,
            "비밀번호는 8~20자이며 영문·숫자·특수문자를 각각 1자 이상 포함해야 합니다."),
    REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 동의 항목이 누락되었습니다."),
    REQUIRED_DOCUMENT_MISSING(HttpStatus.BAD_REQUEST, "필수 증빙 서류가 누락되었습니다."),

    // ── 상품 (MUL-91) ──
    // 화면이 항목별로 다르게 안내해야 하는 정책 위반들. 형식 위반은 VALIDATION_FAILED.
    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."),
    CATEGORY_NOT_LEAF(HttpStatus.BAD_REQUEST, "상품에는 소분류(리프) 카테고리만 지정할 수 있습니다."),
    COLOR_DUPLICATED(HttpStatus.BAD_REQUEST, "같은 색상을 두 번 넣을 수 없습니다."),
    SIZE_DUPLICATED(HttpStatus.BAD_REQUEST, "한 색상에 같은 사이즈를 두 번 넣을 수 없습니다."),
    OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "색상 옵션과 사이즈는 1개 이상이어야 합니다."),
    PRICE_REQUIRED(HttpStatus.BAD_REQUEST, "게시하려면 모든 옵션의 판매가가 필요합니다."),
    INVARIANT_VIOLATED(HttpStatus.BAD_REQUEST, "요청이 상품 구성과 맞지 않습니다."),
    // 재고·주문 쪽에 물려 있는 variant 는 못 지운다 — 화면이 항목별로 다른 안내를 띄운다
    VARIANT_HAS_STOCK(HttpStatus.CONFLICT, "재고가 남아 있는 옵션은 뺄 수 없습니다."),
    VARIANT_ALLOCATED(HttpStatus.CONFLICT, "포장 배분에 잡혀 있는 옵션은 뺄 수 없습니다."),
    VARIANT_HAS_BACKORDER(HttpStatus.CONFLICT, "미송이 걸려 있는 옵션은 뺄 수 없습니다."),
    VARIANT_IN_PENDING_ORDER(HttpStatus.CONFLICT, "처리 중인 주문에 들어 있는 옵션은 뺄 수 없습니다."),

    // ── 로그인 (MUL-69) ──
    // 이메일이 없는 것과 비밀번호가 틀린 것을 구분하지 않는다. 나눠서 알려주면 밖에서
    // 이메일만 넣어보며 가입 여부를 확인할 수 있다(계정 열거). 그래서 실패는 이 하나뿐이다.
    // UNAUTHENTICATED 도 401 이지만 그건 "세션이 없다"는 뜻이라 문구가 다르다 —
    // 로그인 폼 밑에 "인증이 필요합니다."를 띄울 수는 없다.
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
