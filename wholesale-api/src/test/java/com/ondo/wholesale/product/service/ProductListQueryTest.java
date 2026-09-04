package com.ondo.wholesale.product.service;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 목록 쿼리 파라미터의 형식 규칙과 정렬 파싱 단위 검증 (MUL-92). */
class ProductListQueryTest {

    @Test
    void 기본_정렬은_createdAt_내림차순에_id_타이브레이커다() {
        Sort sort = of(0, 20, null).sort();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 방향을_생략하면_오름차순이다() {
        assertThat(of(0, 20, "name").sort().getOrderFor("name").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void 방향은_대소문자를_가리지_않는다() {
        assertThat(of(0, 20, "productNumber,DESC").sort().getOrderFor("productNumber").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 모르는_정렬_키는_400이다() {
        검증_실패한다(() -> of(0, 20, "hack,desc"));
    }

    @Test
    void 모르는_방향은_400이다() {
        검증_실패한다(() -> of(0, 20, "name,sideways"));
    }

    @Test
    void size는_100까지_되고_넘으면_400이다() {
        assertThat(of(0, 100, null).size()).isEqualTo(100);
        검증_실패한다(() -> of(0, 101, null));
    }

    @Test
    void 기간은_같은_날이_되고_뒤집히면_400이다() {
        LocalDate day = LocalDate.of(2026, 9, 4);
        assertThat(ProductListQuery.of(null, null, day, day, 0, 20, null).from()).isEqualTo(day);
        검증_실패한다(() -> ProductListQuery.of(null, null, day.plusDays(1), day, 0, 20, null));
    }

    private ProductListQuery of(int page, int size, String sort) {
        return ProductListQuery.of(null, null, null, null, page, size, sort);
    }

    private void 검증_실패한다(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
