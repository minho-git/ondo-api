package com.ondo.wholesale.outbound.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출고 문서의 확정 전이 단위 검증 (MUL-49). 상태 컬럼이 없다 — shippedAt 의
 * null 여부가 포장완료/출고완료를 가른다 (D-074).
 */
class OutboundTest {

    @Test
    void ship이_출고일시와_장끼번호를_채운다() {
        Outbound outbound = Outbound.builder()
                .wholesalerId(1L).partnerId(2L).outboundNumber(7).build();
        OffsetDateTime shippedAt = OffsetDateTime.now();

        outbound.ship(shippedAt, 3);

        assertThat(outbound.getShippedAt()).isEqualTo(shippedAt);
        assertThat(outbound.getStatementNumber()).isEqualTo(3);
    }
}
