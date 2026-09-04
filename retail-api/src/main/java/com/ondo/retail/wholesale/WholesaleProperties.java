package com.ondo.retail.wholesale;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 도매 서버를 부를 때 쓰는 값 (MUL-88).
 *
 * <p>주소를 설정으로 뺀 건 환경마다 다르기 때문이다. 로컬은 localhost:8081 이고,
 * 배포는 내부 ALB 주소가 된다 — 그건 MUL-87 에서 붙인다.
 *
 * @param baseUrl        도매 API 의 뿌리 주소. 경로는 안 붙인다
 * @param connectTimeout 연결까지. 도매가 아예 안 떠 있으면 여기서 끊긴다
 * @param readTimeout    응답까지. 도매가 받기는 했는데 안 돌려줄 때 여기서 끊는다.
 *                       길게 잡으면 도매 하나가 느릴 때 소매 스레드가 같이 묶인다
 */
@ConfigurationProperties(prefix = "ondo.wholesale")
public record WholesaleProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public WholesaleProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
    }
}
