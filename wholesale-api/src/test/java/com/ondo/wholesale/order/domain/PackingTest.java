package com.ondo.wholesale.order.domain;

import com.ondo.wholesale.order.PackingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포장의 출고 전이 단위 검증 (MUL-49). 409 매핑은 출고 생성 서비스 몫이고,
 * 여기는 도메인이 마지막 그물로 잘못된 전이를 거부하는지 본다.
 */
class PackingTest {

    @Test
    void pack은_READY에서만_PACKED로_전이한다() {
        Packing packing = Packing.builder().orderId(1L).build();

        packing.pack(9L);

        assertThat(packing.getStatus()).isEqualTo(PackingStatus.PACKED);
        assertThat(packing.getOutboundId()).isEqualTo(9L);
        // 이미 묶인 포장은 다시 묶을 수 없다
        assertThatThrownBy(() -> packing.pack(10L)).isInstanceOf(IllegalStateException.class);
    }
}
