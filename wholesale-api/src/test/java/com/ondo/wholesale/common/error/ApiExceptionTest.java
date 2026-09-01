package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커스텀 예외 베이스 불변식 검증.
 */
class ApiExceptionTest {

    @Test
    void errors가_null이면_빈_리스트로_정규화한다() {
        ApiException ex = new ApiException(ErrorCode.INTERNAL_ERROR, "메시지", null);

        assertThat(ex.errors()).isEmpty();
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ex.getMessage()).isEqualTo("메시지");
    }

    @Test
    void 메시지_생략_생성자는_기본_메시지를_쓴다() {
        ApiException ex = new ApiException(ErrorCode.NOT_APPROVED);

        assertThat(ex.getMessage()).isEqualTo(ErrorCode.NOT_APPROVED.defaultMessage());
    }
}
