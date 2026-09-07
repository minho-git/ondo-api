package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.BackorderAllocationRequest.BackorderAllocationItem;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.order.domain.BackorderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미송 배분 요청 검증 단위 테스트 (MUL-48).
 *
 * <p>검증은 단계별 전건 수집이다 — 형식 → 중복 → 미존재 → 열림 → 원래 미송량 →
 * 잔량 → SKU 합계 가용. 한 단계가 걸리면 그 단계에서 걸린 항목 전부를 errors 에
 * 담고, 뒷 단계는 보지 않는다(대표 코드가 섞이지 않게).
 */
class BackorderAllocationValidatorTest {

    private final BackorderAllocationValidator validator = new BackorderAllocationValidator();

    // 미송: (id=61, OPEN, 원래 5, 잔여 3, SKU 901), (id=62, OPEN, 원래 4, 잔여 4, SKU 901)
    private final Map<Long, BackorderAllocationValidator.BackorderLine> 미송들 = Map.of(
            61L, new BackorderAllocationValidator.BackorderLine(61L, BackorderStatus.OPEN, 5, 3, 901L),
            62L, new BackorderAllocationValidator.BackorderLine(62L, BackorderStatus.OPEN, 4, 4, 901L));

    private final Map<Long, Integer> 가용재고 = Map.of(901L, 5);

    @Test
    void 빈_items는_400_INVARIANT_VIOLATED다() {
        assertThatThrownBy(() -> validator.validateShape(null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVARIANT_VIOLATED));
        assertThatThrownBy(() -> validator.validateShape(List.of()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVARIANT_VIOLATED));
    }

    @Test
    void 중복_backorderId는_걸린_항목_전부를_errors에_담는다() {
        List<BackorderAllocationItem> items = List.of(
                new BackorderAllocationItem(61L, 1), new BackorderAllocationItem(61L, 1),
                new BackorderAllocationItem(62L, 1), new BackorderAllocationItem(62L, 1));

        assertThatThrownBy(() -> validator.validateShape(items))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.DUPLICATE_BACKORDER);
                    assertThat(e.errors()).hasSize(2);
                    assertThat(e.errors().get(0).reason()).contains("61");
                    assertThat(e.errors().get(1).reason()).contains("62");
                });
    }

    @Test
    void 열려있지_않은_미송은_409_BACKORDER_NOT_OPEN이다() {
        Map<Long, BackorderAllocationValidator.BackorderLine> 해소포함 = Map.of(
                61L, 미송들.get(61L),
                63L, new BackorderAllocationValidator.BackorderLine(
                        63L, BackorderStatus.RESOLVED, 5, 0, 901L));

        assertThatThrownBy(() -> validator.validateAgainstState(List.of(
                        new BackorderAllocationItem(61L, 1), new BackorderAllocationItem(63L, 1)),
                해소포함, 가용재고))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BACKORDER_NOT_OPEN);
                    assertThat(e.errors()).hasSize(1);
                    assertThat(e.errors().get(0).reason()).contains("63");
                });
    }

    @Test
    void 원래_미송량_초과는_400_잔량_초과는_409다() {
        // id=61 은 원래 5, 잔여 3 — 6 은 원래 미송량 초과(요청 오류), 4 는 잔량 초과(상태 충돌)
        assertThatThrownBy(() -> validator.validateAgainstState(
                List.of(new BackorderAllocationItem(61L, 6)), 미송들, 가용재고))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ALLOCATION_EXCEEDS_ORDER));

        assertThatThrownBy(() -> validator.validateAgainstState(
                List.of(new BackorderAllocationItem(61L, 4)), 미송들, 가용재고))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ALLOCATION_EXCEEDS_REMAINING));
    }

    @Test
    void 같은_SKU_합계가_가용재고를_넘으면_INSUFFICIENT_STOCK이고_field는_items다() {
        // 각자는 잔여 안(3·4)이지만 같은 SKU 합 7 이 가용 5 를 넘는다
        assertThatThrownBy(() -> validator.validateAgainstState(List.of(
                        new BackorderAllocationItem(61L, 3), new BackorderAllocationItem(62L, 4)),
                미송들, 가용재고))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.errors()).hasSize(1);
                    assertThat(e.errors().get(0).field()).isEqualTo("items");
                });
    }

    @Test
    void 앞_단계_실패가_있으면_뒷_단계는_보지_않는다() {
        // 미존재(64)와 잔량 초과(61에 4)가 섞이면 앞 단계인 404 만 나간다
        assertThatThrownBy(() -> validator.validateAgainstState(List.of(
                        new BackorderAllocationItem(61L, 4), new BackorderAllocationItem(64L, 1)),
                미송들, 가용재고))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(e.errors()).hasSize(1);
                    assertThat(e.errors().get(0).reason()).contains("64");
                });
    }
}
