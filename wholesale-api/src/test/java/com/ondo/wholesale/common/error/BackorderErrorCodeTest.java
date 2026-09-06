package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미송 에러 코드(MUL-48)가 계약이 약속한 HTTP 상태를 갖는지 못박는다.
 *
 * <p>계약 원본은 backorder/ 스텁의 {@code @Operation} 자바독이다. 재고·출고·미송을
 * 병렬로 구현하기로 해서 실구현보다 먼저 등록했다 — 실제 HTTP 응답의 {@code $.code} 는
 * 각 API 구현 커밋의 슬라이스·통합 테스트가 잡는다.
 */
class BackorderErrorCodeTest {

    /** 요청 자체가 잘못된 것들 — 미송 상태와 무관하게 400. */
    enum Backorder400Code {
        DUPLICATE_BACKORDER
    }

    /** 지금 상태와 충돌하는 것들 — 같은 요청도 상태가 바뀌면 성공할 수 있어 409. */
    enum Backorder409Code {
        BACKORDER_NOT_OPEN
    }

    @ParameterizedTest
    @EnumSource(Backorder400Code.class)
    void 미송_400군_코드는_전부_BAD_REQUEST다(Backorder400Code expected) {
        assertThat(ErrorCode.valueOf(expected.name()).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(Backorder409Code.class)
    void 미송_409군_코드는_전부_CONFLICT다(Backorder409Code expected) {
        assertThat(ErrorCode.valueOf(expected.name()).status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 화면에_내보낼_문구가_비어_있지_않다() {
        for (Backorder400Code code : Backorder400Code.values()) {
            assertThat(ErrorCode.valueOf(code.name()).defaultMessage()).isNotBlank();
        }
        for (Backorder409Code code : Backorder409Code.values()) {
            assertThat(ErrorCode.valueOf(code.name()).defaultMessage()).isNotBlank();
        }
    }
}
