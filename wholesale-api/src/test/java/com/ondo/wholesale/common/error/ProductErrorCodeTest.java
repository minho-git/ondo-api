package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 에러 코드(MUL-91)가 계약이 약속한 HTTP 상태를 갖는지 확인.
 * 코드 문자열은 enum 이름 그대로라 이름 오타는 컴파일로 잡힌다.
 */
class ProductErrorCodeTest {

    @Test
    void TRANSITION_NOT_ALLOWED는_CONFLICT다() {
        assertThat(ErrorCode.TRANSITION_NOT_ALLOWED.status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void variant_409군_코드는_전부_CONFLICT다() {
        assertThat(new ErrorCode[]{
                ErrorCode.VARIANT_HAS_STOCK, ErrorCode.VARIANT_ALLOCATED,
                ErrorCode.VARIANT_HAS_BACKORDER, ErrorCode.VARIANT_IN_PENDING_ORDER,
        }).allSatisfy(code -> assertThat(code.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 상품_400군_코드는_전부_BAD_REQUEST다() {
        assertThat(new ErrorCode[]{
                ErrorCode.CATEGORY_NOT_FOUND, ErrorCode.CATEGORY_NOT_LEAF,
                ErrorCode.COLOR_DUPLICATED, ErrorCode.SIZE_DUPLICATED,
                ErrorCode.OPTION_REQUIRED, ErrorCode.PRICE_REQUIRED,
                ErrorCode.INVARIANT_VIOLATED,
        }).allSatisfy(code -> assertThat(code.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
