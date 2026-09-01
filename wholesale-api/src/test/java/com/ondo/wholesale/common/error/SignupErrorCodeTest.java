package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 에러 코드가 티켓 표대로인지 못박는다 (MUL-68).
 *
 * <p>티켓은 6종을 <b>전부 400</b> 으로 명시했다. 소매(MUL-78)는 이메일 중복을 409 로
 * 냈는데, "중복이니 409 가 맞지 않나" 하고 나중에 누가 바꾸면 프론트가 조용히 깨진다.
 * 에러 코드와 상태는 API 계약이라 테스트로 고정한다.
 */
class SignupErrorCodeTest {

    /** 티켓 "에러" 표에 있는 6종. */
    enum SignupCode {
        EMAIL_DUPLICATED,
        BIZ_REG_NO_DUPLICATED,
        PASSWORD_POLICY_VIOLATED,
        REQUIRED_CONSENT_MISSING,
        REQUIRED_DOCUMENT_MISSING,
        VALIDATION_FAILED
    }

    @ParameterizedTest
    @EnumSource(SignupCode.class)
    void 가입_에러는_전부_400_이다(SignupCode expected) {
        ErrorCode code = ErrorCode.valueOf(expected.name());

        assertThat(code.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(SignupCode.class)
    void 화면에_내보낼_문구가_비어_있지_않다(SignupCode expected) {
        assertThat(ErrorCode.valueOf(expected.name()).defaultMessage()).isNotBlank();
    }
}
