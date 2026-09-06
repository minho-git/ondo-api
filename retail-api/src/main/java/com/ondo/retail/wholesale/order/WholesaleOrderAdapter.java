package com.ondo.retail.wholesale.order;

import com.ondo.retail.order.OrderClient;
import com.ondo.retail.order.dto.WholesaleOrderCommand;
import com.ondo.retail.order.dto.WholesaleOrderReceipt;
import com.ondo.retail.wholesale.dto.WholesaleEnvelope;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderCreateRequest;
import com.ondo.retail.wholesale.order.dto.WholesaleOrderCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 도매 주문 접수 API 를 소매 말로 옮긴다 (MUL-98).
 *
 * <p><b>여기는 예외를 안 던진다.</b> 상품·미송 어댑터와 다른 점이다.
 *
 * <p>상품이 안 읽히면 화면 전체가 못 그려지니 사고다. 그런데 주문은 도매처 하나가
 * 거절해도 나머지는 접수돼야 하고, 거절 사유가 그대로 화면에 뜬다 — 사고가 아니라
 * 결과다. 예외로 만들면 부르는 쪽이 try/catch 로 결과를 조립하게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WholesaleOrderAdapter implements OrderClient {

    /**
     * 도매가 "이미 받았다" 고 할 때의 코드.
     *
     * <p>소매가 재시도했다는 뜻이라 <b>성공으로 친다.</b> 주문은 이미 도매 장부에 있다.
     * 다만 도매가 409 만 주고 그 주문의 내용은 안 줘서 번호·금액을 못 채운다 —
     * 「같은 열쇠로 다시 오면 처음 결과 그대로」(숙제 7번)를 하면 그때 채워진다.
     */
    private static final String ALREADY_CREATED = "ORDER_ALREADY_CREATED";

    private final WholesaleOrderApi api;

    @Override
    public WholesaleOrderReceipt place(WholesaleOrderCommand command) {
        try {
            WholesaleEnvelope<WholesaleOrderCreated> response = api.create(toRequest(command));
            WholesaleOrderCreated created = response.data();
            return WholesaleOrderReceipt.accepted(created.id(), created.orderNumber(), created.orderAmount());

        } catch (RestClientResponseException e) {
            // 도매가 우리 규약대로 대답한 경우다. 사유를 그대로 옮긴다
            return fromError(command, e);

        } catch (RestClientException e) {
            // 도매가 아예 안 뜨거나 제때 대답을 안 한 경우. 사용자가 다시 누르면 된다
            log.warn("도매 접수 호출 실패. wholesalerId={} retailOrderId={}",
                    command.wholesalerId(), command.retailOrderId(), e);
            return WholesaleOrderReceipt.rejected("UPSTREAM_UNAVAILABLE",
                    "도매처에 접수하지 못했어요. 장바구니에 그대로 있어요");
        }
    }

    /**
     * 도매의 에러 응답을 결과로 옮긴다.
     *
     * <p>문구를 우리가 다시 쓰지 않고 도매 것을 그대로 쓴다. "판매가가 바뀌었습니다"
     * 같은 건 도매가 자기 상태를 보고 만든 말이라 우리가 더 정확히 쓸 수 없다.
     */
    private WholesaleOrderReceipt fromError(WholesaleOrderCommand command, RestClientResponseException e) {
        WholesaleError error = readError(e);

        if (ALREADY_CREATED.equals(error.code())) {
            log.info("이미 접수된 주문이다. 소매가 재시도한 것으로 본다. wholesalerId={} retailOrderId={}",
                    command.wholesalerId(), command.retailOrderId());
            return new WholesaleOrderReceipt(true, null, null, null, null, "이미 접수된 주문이에요");
        }

        log.info("도매가 주문을 거절했다. wholesalerId={} code={}", command.wholesalerId(), error.code());
        return WholesaleOrderReceipt.rejected(error.code(), error.message());
    }

    /**
     * 도매 에러 본문을 읽는다.
     *
     * <p>못 읽어도 던지지 않는다. 도매가 아니라 앞단(ALB·프록시)이 대답했으면 우리
     * 규약이 아닌 게 오는데, 그걸로 주문 전체를 실패시킬 이유가 없다.
     */
    private WholesaleError readError(RestClientResponseException e) {
        try {
            WholesaleError body = e.getResponseBodyAs(WholesaleError.class);
            if (body != null && body.code() != null) {
                return body;
            }
        } catch (RuntimeException ignored) {
            // 아래 기본값으로 떨어진다
        }
        log.warn("도매 에러 본문을 못 읽었다. status={}", e.getStatusCode());
        return new WholesaleError("UPSTREAM_ERROR", "도매처에 접수하지 못했어요. 장바구니에 그대로 있어요");
    }

    private static WholesaleOrderCreateRequest toRequest(WholesaleOrderCommand command) {
        return new WholesaleOrderCreateRequest(
                command.retailOrderId(),
                command.retailerId(),
                command.wholesalerId(),
                command.retailerName(),
                command.retailerPhone(),
                command.paymentTerm(),
                command.receiveMethod(),
                command.agentName(),
                command.agentPhone(),
                command.items().stream()
                        .map(l -> new WholesaleOrderCreateRequest.Item(l.variantId(), l.qty(), l.expectedUnitPrice()))
                        .toList());
    }

    /** 도매 실패 응답의 봉투. 성공 봉투(data)와 모양이 다르다. */
    private record WholesaleError(String code, String message) {}
}
