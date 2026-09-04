package com.ondo.retail.wholesale;

/**
 * 도매를 부르다 실패했을 때 (MUL-88).
 *
 * <p>도매가 안 떠 있거나 · 5xx 를 주거나 · 제때 안 돌려줄 때 던진다.
 * 소매 잘못이 아니라 소매도 어쩔 수 없는 상황이라 사용자에겐 "잠시 후 다시" 로 나가고,
 * 원인은 로그에 남긴다 ({@code GlobalExceptionHandler}).
 *
 * <p>예외는 상품 상세의 404 뿐이다. 그건 "그런 상품 없음" 이라는 정상 흐름이라
 * 어댑터가 빈 값으로 바꾼다. 나머지 4xx 는 소매가 요청을 잘못 만든 것이므로
 * 조용히 넘기지 않고 여기로 던져 로그에 남긴다.
 */
public class WholesaleApiException extends RuntimeException {

    public WholesaleApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
