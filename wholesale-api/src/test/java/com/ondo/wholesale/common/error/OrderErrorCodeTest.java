package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 에러 코드(MUL-47)가 계약이 약속한 HTTP 상태를 갖는지 못박는다.
 *
 * <p>계약 원본은 order/ 스텁의 {@code @Operation} 자바독이다. {@code ALLOCATION_EXCEEDS_ORDER}는
 * 확정 스텁이 400, 포장 준비 스텁이 409 로 서로 다르게 적었었는데, 배분 합이 주문 수량을
 * 넘는 것은 서버 상태와 무관한 요청 오류라 <b>400 으로 통일</b>했다(포장 준비 표기를 수정).
 *
 * <p>코드 문자열은 enum 이름 그대로라 이름 오타는 컴파일로 잡힌다. 실제 HTTP 응답의
 * {@code $.code} 는 각 API 구현 커밋의 슬라이스·통합 테스트가 잡는다.
 */
class OrderErrorCodeTest {

    /** 요청 자체가 잘못된 것들 — 주문·배분 상태와 무관하게 400. */
    enum Order400Code {
        ORDER_ITEM_MISSING,
        ORDER_ITEM_NOT_IN_ORDER,
        DUPLICATE_ORDER_ITEM,
        ALLOCATION_EXCEEDS_ORDER
    }

    /** 지금 상태와 충돌하는 것들 — 같은 요청도 상태가 바뀌면 성공할 수 있어 409. */
    enum Order409Code {
        ALLOCATION_EXCEEDS_REMAINING,
        INSUFFICIENT_STOCK,
        DOCUMENT_FINALIZED
    }

    @ParameterizedTest
    @EnumSource(Order400Code.class)
    void 주문_400군_코드는_전부_BAD_REQUEST다(Order400Code expected) {
        assertThat(ErrorCode.valueOf(expected.name()).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(Order409Code.class)
    void 주문_409군_코드는_전부_CONFLICT다(Order409Code expected) {
        assertThat(ErrorCode.valueOf(expected.name()).status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 화면에_내보낼_문구가_비어_있지_않다() {
        for (Order400Code code : Order400Code.values()) {
            assertThat(ErrorCode.valueOf(code.name()).defaultMessage()).isNotBlank();
        }
        for (Order409Code code : Order409Code.values()) {
            assertThat(ErrorCode.valueOf(code.name()).defaultMessage()).isNotBlank();
        }
    }
}
