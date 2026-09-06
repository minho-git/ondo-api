package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출고 에러 코드(MUL-49)가 계약이 약속한 HTTP 상태를 갖는지 못박는다.
 *
 * <p>계약 원본은 outbound/ 스텁의 {@code @Operation} 자바독이다. 재고·출고·미송을
 * 병렬로 구현하기로 해서 실구현보다 먼저 등록했다 — 실제 HTTP 응답의 {@code $.code} 는
 * 각 API 구현 커밋의 슬라이스·통합 테스트가 잡는다.
 */
class OutboundErrorCodeTest {

    /** 요청 자체가 잘못된 것들 — 출고 상태와 무관하게 400. */
    enum Outbound400Code {
        DUPLICATE_PACKING_ITEM,
        RETAILER_MIXED,
        RECEIVE_BY_MIXED
    }

    /** 지금 상태와 충돌하는 것들 — 같은 요청도 상태가 바뀌면 성공할 수 있어 409. */
    enum Outbound409Code {
        PACKING_NOT_READY,
        OUTBOUND_EMPTY
    }

    @ParameterizedTest
    @EnumSource(Outbound400Code.class)
    void 출고_400군_코드는_전부_BAD_REQUEST다(Outbound400Code expected) {
        assertThat(ErrorCode.valueOf(expected.name()).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(Outbound409Code.class)
    void 출고_409군_코드는_전부_CONFLICT다(Outbound409Code expected) {
        assertThat(ErrorCode.valueOf(expected.name()).status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 화면에_내보낼_문구가_비어_있지_않다() {
        for (Outbound400Code code : Outbound400Code.values()) {
            assertThat(ErrorCode.valueOf(code.name()).defaultMessage()).isNotBlank();
        }
        for (Outbound409Code code : Outbound409Code.values()) {
            assertThat(ErrorCode.valueOf(code.name()).defaultMessage()).isNotBlank();
        }
    }
}
