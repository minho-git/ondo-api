package com.ondo.retail.order;

import com.ondo.retail.cart.CartItemRepository;
import com.ondo.retail.cart.domain.CartItem;
import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.order.domain.OrderGroup;
import com.ondo.retail.order.domain.OrderGroupStatus;
import com.ondo.retail.order.dto.PlaceOrderRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수의 DB 쓰기만 모은다 (MUL-98).
 *
 * <p><b>{@link OrderPlaceService} 와 나눠 둔 이유가 트랜잭션이다.</b> 같은 클래스 안에서
 * 부르면 {@code @Transactional} 이 안 걸린다 — 스프링은 프록시로 가로채는데 자기 호출은
 * 프록시를 안 지난다. 도매 호출을 트랜잭션 밖에 두려면 빈이 갈라져 있어야 한다.
 *
 * <p>그래서 쓰기가 둘로 끊긴다. 그 사이에 도매를 부른다.
 *
 * <pre>
 *   open()    주문서를 만들고 커밋      도매를 부르려면 이 id 가 필요하다
 *   ...       도매 호출 (트랜잭션 밖)
 *   settle()  금액 · 상태 · 장바구니 정리
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderGroupWriter {

    private final OrderGroupRepository orderGroupRepository;
    private final OrderNoGenerator orderNoGenerator;
    private final CartItemRepository cartItemRepository;

    /**
     * 주문서를 연다. 같은 멱등키로 다시 왔으면 있던 걸 이어 쓴다.
     *
     * <p><b>「처음 결과 그대로」는 아직 안 한다</b>(숙제 7번). 실패한 도매처의 사유를
     * 어디에도 안 남겨서 재현할 수가 없다. 대신 부르는 쪽이 <b>도매를 다시 부른다</b> —
     * 이미 받은 곳은 도매가 막고, 실패했던 곳만 새로 들어간다. 데이터는 안 깨지고
     * 결과적으로 재시도가 된다. 다만 응답의 실패 사유는 "이번 시도" 기준이라
     * 처음과 다를 수 있다.
     */
    @Transactional
    public OrderGroup open(Long retailerId, String idempotencyKey, PlaceOrderRequest request) {
        String requestId = (idempotencyKey == null || idempotencyKey.isBlank())
                // 열쇠를 안 보내면 연타를 막을 방법이 없다. 그래도 접수는 되게 두되
                // 서로 다른 주문으로 본다 — request_id 가 NOT NULL 이라 값은 있어야 한다
                ? "no-key-" + UUID.randomUUID()
                : idempotencyKey;

        return orderGroupRepository.findByRequestId(requestId)
                .map(existing -> {
                    // 남의 열쇠를 주워 쓰면 남의 주문서에 주문을 붙이게 된다
                    if (!existing.getRetailerId().equals(retailerId)) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                    }
                    log.info("같은 멱등키로 다시 왔다. 도매를 다시 부른다. orderId={}", existing.getId());
                    return existing;
                })
                .orElseGet(() -> create(retailerId, requestId, request));
    }

    /**
     * 새 주문서를 만든다.
     *
     * <p>{@code UNIQUE(request_id)} 위반을 잡아 다시 읽는 이유 — <b>진짜 연타</b>는 두 요청이
     * 거의 동시에 온다. 둘 다 위에서 "없다" 를 보고 둘 다 만들려 들면 하나가 제약에 걸려
     * 500 이 난다. 연타를 막으라고 둔 열쇠가 연타 때 터지는 셈이다.
     */
    private OrderGroup create(Long retailerId, String requestId, PlaceOrderRequest request) {
        OffsetDateTime orderedAt = OffsetDateTime.now();
        try {
            return orderGroupRepository.saveAndFlush(OrderGroup.builder()
                    .retailerId(retailerId)
                    .requestId(requestId)
                    .orderNo(orderNoGenerator.next(orderedAt))
                    .agentName(request.agentName())
                    .agentPhone(request.agentPhone())
                    .orderedAt(orderedAt)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("같은 멱등키가 동시에 들어왔다. 먼저 만든 주문서를 쓴다. requestId={}", requestId);
            return orderGroupRepository.findByRequestId(requestId)
                    .orElseThrow(() -> e);
        }
    }

    /** 이미 접수까지 끝난 주문서인지. 연타의 두 번째 요청을 여기서 끊는다. */
    @Transactional(readOnly = true)
    public java.util.Optional<OrderGroup> findAccepted(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return java.util.Optional.empty();
        }
        return orderGroupRepository.findByRequestId(idempotencyKey)
                .filter(g -> g.getStatus() == OrderGroupStatus.ACCEPTED);
    }

    /**
     * 도매 결과를 주문서에 반영한다.
     *
     * <p>접수된 도매처의 장바구니 줄만 뺀다. 실패한 줄까지 빼면 사용자가 다시 담아야
     * 한다 — 도매가 잠깐 못 받은 것뿐인데.
     *
     * @param anyAccepted 한 곳이라도 받아졌는지. 하나도 없으면 {@code FAILED} 로 남긴다.
     *                    지우지 않는 건 왜 실패했는지 남기고 멱등키를 살리기 위해서다
     */
    @Transactional
    public OrderGroup settle(Long orderGroupId, long acceptedAmount, boolean anyAccepted,
                             List<CartItem> acceptedCartItems) {
        OrderGroup group = orderGroupRepository.findById(orderGroupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        group.settle(acceptedAmount, anyAccepted);
        if (!acceptedCartItems.isEmpty()) {
            cartItemRepository.deleteAll(acceptedCartItems);
        }
        return group;
    }
}
