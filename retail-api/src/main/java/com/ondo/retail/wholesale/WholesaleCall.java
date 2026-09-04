package com.ondo.retail.wholesale;

import java.util.function.Supplier;
import org.springframework.web.client.RestClientException;

/**
 * 도매 호출을 감싸 실패를 하나로 모은다 (MUL-88 · MUL-97).
 *
 * <p>{@link RestClientException} 하나로 잡는 이유 — 도매가 안 떠 있는 것도(연결 거부),
 * 제때 안 주는 것도(타임아웃), 5xx 도 소매 입장에선 같은 일이다. 소매가 할 수 있는 게
 * 없고 사용자에게 할 말도 같다.
 *
 * <p>4xx 중 따로 다뤄야 하는 것(상세의 404 같은)은 부르는 쪽에서 이걸 안 거치고
 * 직접 잡는다. 여기서 다 삼키면 "없는 상품" 이 "도매 장애" 로 바뀐다.
 */
public final class WholesaleCall {

    private WholesaleCall() {
    }

    /**
     * @param what 실패 로그에 남길 요청 이름. "상품 목록" 처럼 사람이 읽는 말로 적는다
     */
    public static <T> T call(String what, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new WholesaleApiException("도매를 부르지 못했습니다. 요청=" + what, e);
        }
    }
}
