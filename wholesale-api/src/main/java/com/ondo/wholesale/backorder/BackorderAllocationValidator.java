package com.ondo.wholesale.backorder;

import com.ondo.wholesale.backorder.dto.BackorderAllocationRequest.BackorderAllocationItem;
import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponse;
import com.ondo.wholesale.order.domain.BackorderStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 미송 배분 요청 검증 (MUL-48). 부분 성공이 없는 배치라 단계별 <b>전건 수집</b>이다 —
 * 한 단계에서 걸린 항목 전부를 errors 에 담아 사장님이 한 번에 고치게 한다.
 *
 * <p>단계 순서는 형식(400) → 중복(400) → 미존재(404) → 열림(409) → 원래 미송량(400) →
 * 잔량(409) → SKU 합계 가용(409). 앞 단계가 걸리면 뒷 단계는 보지 않는다 —
 * 혼합 실패의 대표 코드가 섞이지 않게 하기 위해서다.
 */
@Component
public class BackorderAllocationValidator {

    /** 검증에 필요한 미송 사실만 뽑은 값 — 엔티티 없이 순수 단위 테스트가 되게 한다. */
    public record BackorderLine(Long backorderId, BackorderStatus status, int qty,
                                int remainingQty, Long variantId) {
    }

    /** 형식·중복 — DB 를 보기 전에 요청만으로 끝나는 단계. */
    public List<BackorderAllocationItem> validateShape(List<BackorderAllocationItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(ErrorCode.INVARIANT_VIOLATED, "미송 항목을 1건 이상 담아야 합니다.");
        }
        List<ErrorResponse.FieldError> shapeErrors = new ArrayList<>();
        for (BackorderAllocationItem item : items) {
            if (item.backorderId() == null || item.allocateQty() == null || item.allocateQty() < 1) {
                shapeErrors.add(new ErrorResponse.FieldError("items",
                        "backorderId 와 1 이상의 allocateQty 가 필요합니다: " + item));
            }
        }
        if (!shapeErrors.isEmpty()) {
            throw new ApiException(ErrorCode.INVARIANT_VIOLATED,
                    ErrorCode.INVARIANT_VIOLATED.defaultMessage(), shapeErrors);
        }

        Set<Long> seen = new HashSet<>();
        Set<Long> duplicated = new LinkedHashSet<>();
        for (BackorderAllocationItem item : items) {
            if (!seen.add(item.backorderId())) {
                duplicated.add(item.backorderId());
            }
        }
        if (!duplicated.isEmpty()) {
            throw new ApiException(ErrorCode.DUPLICATE_BACKORDER,
                    ErrorCode.DUPLICATE_BACKORDER.defaultMessage(),
                    duplicated.stream().map(id -> new ErrorResponse.FieldError(
                            "items.backorderId", "같은 미송을 두 번 담았습니다: " + id)).toList());
        }
        return items;
    }

    /**
     * 미존재부터 SKU 합계까지 — 주문·variant 락 아래에서 읽은 상태와의 검증.
     *
     * @param lines             도매처 스코프로 찾은 미송 사실. 여기 없는 id 는 미존재(남의 것 포함)다.
     * @param availableByVariant SKU 별 가용재고(재고 − 예약). variant 락 아래에서 읽은 값이다.
     */
    public void validateAgainstState(List<BackorderAllocationItem> items,
                                     Map<Long, BackorderLine> lines,
                                     Map<Long, Integer> availableByVariant) {
        failAll(ErrorCode.RESOURCE_NOT_FOUND, items.stream()
                .filter(item -> lines.get(item.backorderId()) == null)
                .map(item -> new ErrorResponse.FieldError("items.backorderId",
                        "미송이 없거나 접근할 수 없습니다: " + item.backorderId()))
                .toList());
        failAll(ErrorCode.BACKORDER_NOT_OPEN, items.stream()
                .filter(item -> lines.get(item.backorderId()).status() != BackorderStatus.OPEN)
                .map(item -> new ErrorResponse.FieldError("items.backorderId",
                        "열려 있는 미송이 아닙니다: " + item.backorderId()))
                .toList());
        failAll(ErrorCode.ALLOCATION_EXCEEDS_ORDER, items.stream()
                .filter(item -> item.allocateQty() > lines.get(item.backorderId()).qty())
                .map(item -> new ErrorResponse.FieldError("items.allocateQty",
                        "배분 수량이 원래 미송량을 넘습니다: " + item.backorderId()))
                .toList());
        failAll(ErrorCode.ALLOCATION_EXCEEDS_REMAINING, items.stream()
                .filter(item -> item.allocateQty() > lines.get(item.backorderId()).remainingQty())
                .map(item -> new ErrorResponse.FieldError("items.allocateQty",
                        "배분 수량이 남은 미송량을 넘습니다: " + item.backorderId()))
                .toList());

        // 같은 SKU 를 쓰는 항목의 합이 가용을 넘는지 — 특정 항목의 잘못이 아니라 field 는 items 다
        Map<Long, Integer> requestedByVariant = new HashMap<>();
        for (BackorderAllocationItem item : items) {
            requestedByVariant.merge(lines.get(item.backorderId()).variantId(),
                    item.allocateQty(), Integer::sum);
        }
        failAll(ErrorCode.INSUFFICIENT_STOCK, requestedByVariant.entrySet().stream()
                .filter(entry -> entry.getValue() > availableByVariant.get(entry.getKey()))
                .map(entry -> new ErrorResponse.FieldError("items",
                        "SKU " + entry.getKey() + " 의 요청 합 " + entry.getValue()
                                + " 이 가용재고를 넘습니다."))
                .toList());
    }

    /** 한 단계에서 걸린 항목이 있으면 전부 담아 던진다 — 뒷 단계는 보지 않는다. */
    private void failAll(ErrorCode code, List<ErrorResponse.FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(code, code.defaultMessage(), errors);
        }
    }
}
