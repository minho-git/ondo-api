package com.ondo.wholesale.outbound.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 출고 목록 쿼리 파라미터의 형식 규칙과 정렬 파싱 단위 검증 (MUL-49).
 *
 * <p>status 는 컨트롤러 시그니처가 {@code OutboundStatusFilter} 타입이라 여기서 파싱하지
 * 않는다 — 미정의 값 400 은 타입 미스매치 핸들러 소관이다 (OrderListQuery 전례).
 */
class OutboundListQueryTest {

    @Test
    void 정렬_화이트리스트() {
        // 기본은 createdAt 내림차순 + id 타이브레이커
        Sort sort = of(null).sort();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
        // 허용 키는 createdAt·outboundNumber 뿐이다
        assertThat(of("outboundNumber,asc").sort().getOrderFor("outboundNumber").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(of("createdAt").sort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        검증_실패한다(() -> of("hack,desc"));
        검증_실패한다(() -> of("createdAt,sideways"));
    }

    @Test
    void from이_to보다_뒤면_400() {
        LocalDate day = LocalDate.of(2026, 9, 6);
        assertThat(OutboundListQuery.of(null, null, null, day, day, 0, 20, null).from()).isEqualTo(day);
        assertThatThrownBy(() -> OutboundListQuery.of(null, null, null, day.plusDays(1), day, 0, 20, null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).anySatisfy(err -> assertThat(err.field()).isEqualTo("from"));
                });
    }

    private OutboundListQuery of(String sort) {
        return OutboundListQuery.of(null, null, null, null, null, 0, 20, sort);
    }

    private void 검증_실패한다(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(ex.errors()).anySatisfy(err -> assertThat(err.field()).isEqualTo("sort"));
        });
    }
}
