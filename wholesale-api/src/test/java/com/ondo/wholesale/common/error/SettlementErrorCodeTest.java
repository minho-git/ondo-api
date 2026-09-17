package com.ondo.wholesale.common.error;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정산 에러 코드(MUL-124 · MUL-125)가 입금 계약 스텁이 약속한 HTTP 상태를 갖는지 못박는다.
 * 실제 응답의 {@code $.code}는 PaymentCreateIntegrationTest 가 잡는다.
 */
class SettlementErrorCodeTest {

    enum Settlement400Code {
        PAID_AT_IN_FUTURE,
        DUPLICATE_ORDER,
        ORDER_RETAILER_MISMATCH
    }

    enum Settlement409Code {
        ORDER_NOT_CONFIRMED,
        ALLOCATION_EXCEEDS_PAYMENT,
        ALLOCATION_EXCEEDS_OUTSTANDING,
        ALLOCATION_EXCEEDS_PREPAID
    }

    @ParameterizedTest
    @EnumSource(Settlement400Code.class)
    void 정산_400군_코드는_전부_BAD_REQUEST다(Settlement400Code expected) {
        ErrorCode code = ErrorCode.valueOf(expected.name());
        assertThat(code.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code.defaultMessage()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(Settlement409Code.class)
    void 정산_409군_코드는_전부_CONFLICT다(Settlement409Code expected) {
        ErrorCode code = ErrorCode.valueOf(expected.name());
        assertThat(code.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code.defaultMessage()).isNotBlank();
    }
}
