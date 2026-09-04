package com.ondo.retail.backorder;

import com.ondo.retail.backorder.dto.BackorderLine;
import com.ondo.retail.backorder.dto.BackorderResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 미송 응답 조립 (MUL-97).
 *
 * <p>이번 작업에서 새로 생긴 로직은 여기뿐이다 — <b>도매가 준 줄에 소매 주문번호를 붙이는 것</b>.
 * 도매 호출과 SQL 은 각자 자기 테스트에서 보고, 여기서는 둘을 가짜로 세워
 * 합치는 규칙만 확인한다.
 */
class BackorderServiceTest {

    private static final long 우리 = 42L;
    private static final OffsetDateTime 주문시각 = OffsetDateTime.parse("2026-08-30T09:30:00+09:00");

    private final OrderNoQuery orderNoQuery = mock(OrderNoQuery.class);

    @Test
    @DisplayName("도매가 준 줄에 소매 주문번호가 붙는다")
    void 주문번호를_채운다() {
        BackorderService service = service(
                List.of(줄(4402L, 5001L), 줄(4408L, 5008L)),
                Map.of(5001L, "20260830-0930-0085", 5008L, "20260901-1110-0087"));

        List<BackorderResponse> 미송 = service.waiting(우리, PageRequest.of(0, 20)).getContent();

        assertThat(미송).extracting(BackorderResponse::orderNo)
                .containsExactly("20260830-0930-0085", "20260901-1110-0087");
        assertThat(미송.getFirst().orderId()).isEqualTo(5001L);
        assertThat(미송.getFirst().backorderId()).isEqualTo(4402L);
        assertThat(미송.getFirst().wholesaler().name()).isEqualTo("코튼클럽");
    }

    @Test
    @DisplayName("주문번호를 못 찾아도 줄이 사라지지 않는다")
    void 주문번호가_없어도_줄은_남는다() {
        // 도매와 소매의 주문서가 어긋난 상태다. 줄을 버리면 소매처가 기다리는
        // 물건 하나가 화면에서 소리 없이 없어진다
        BackorderService service = service(
                List.of(줄(4402L, 5001L), 줄(4408L, 5008L)),
                Map.of(5001L, "20260830-0930-0085"));

        List<BackorderResponse> 미송 = service.waiting(우리, PageRequest.of(0, 20)).getContent();

        assertThat(미송).hasSize(2);
        assertThat(미송.get(1).orderNo()).isNull();
        assertThat(미송.get(1).backorderId()).isEqualTo(4408L);
    }

    @Test
    @DisplayName("세션에서 받은 소매처 id 를 도매와 주문번호 조회 양쪽에 그대로 넘긴다")
    void 소매처_id_를_양쪽에_넘긴다() {
        Long[] 도매에_넘긴값 = new Long[1];
        BackorderClient client = (retailerId, pageable) -> {
            도매에_넘긴값[0] = retailerId;
            return new PageImpl<>(List.of(줄(4402L, 5001L)), pageable, 1);
        };
        given(orderNoQuery.byOrderIds(anyLong(), any())).willReturn(Map.of(5001L, "20260830-0930-0085"));

        new BackorderService(client, orderNoQuery).waiting(우리, PageRequest.of(0, 20));

        // 한쪽만 걸면 남의 미송에 내 주문번호가 붙거나, 내 미송에 번호가 안 붙는다
        assertThat(도매에_넘긴값[0]).isEqualTo(우리);
        verify(orderNoQuery).byOrderIds(우리, List.of(5001L));
    }

    @Test
    @DisplayName("같은 주문에서 나온 미송이 여럿이어도 주문서는 한 번만 찾는다")
    void 주문서를_중복해서_찾지_않는다() {
        service(List.of(줄(4402L, 5001L), 줄(4403L, 5001L), 줄(4404L, 5008L)),
                Map.of(5001L, "20260830-0930-0085", 5008L, "20260901-1110-0087"))
                .waiting(우리, PageRequest.of(0, 20));

        // 접수 한 번에 미송이 여러 줄 생긴다. 그대로 넘기면 IN 절에 같은 id 가 쌓인다
        verify(orderNoQuery).byOrderIds(우리, List.of(5001L, 5008L));
    }

    @Test
    @DisplayName("전체 개수는 도매가 준 값을 그대로 쓴다")
    void 전체_개수는_도매를_따른다() {
        BackorderClient client = (retailerId, pageable) ->
                new PageImpl<>(List.of(줄(4402L, 5001L)), pageable, 37);
        given(orderNoQuery.byOrderIds(anyLong(), any())).willReturn(Map.of(5001L, "20260830-0930-0085"));

        Page<BackorderResponse> page = new BackorderService(client, orderNoQuery).waiting(우리, PageRequest.of(0, 20));

        // 한 장에 1건만 왔어도 전체는 37건이다. 이걸 놓치면 페이지가 1장으로 보인다
        assertThat(page.getTotalElements()).isEqualTo(37);
    }

    // ── 가짜 ────────────────────────────────────────────────────

    private BackorderService service(List<BackorderLine> lines, Map<Long, String> orderNos) {
        BackorderClient client = (retailerId, pageable) -> new PageImpl<>(lines, pageable, lines.size());
        given(orderNoQuery.byOrderIds(anyLong(), any(Collection.class))).willReturn(orderNos);
        return new BackorderService(client, orderNoQuery);
    }

    private static BackorderLine 줄(long backorderId, long orderId) {
        return new BackorderLine(
                backorderId, orderId, 주문시각,
                new BackorderLine.Wholesaler(11L, "코튼클럽"),
                4380L, "베이직 라운드 니트", "오트밀", "FREE", 4,
                LocalDate.of(2026, 9, 10), "공장 재입고 예정");
    }
}
