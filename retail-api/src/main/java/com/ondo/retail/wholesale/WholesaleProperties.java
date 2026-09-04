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
 * @param secret         소매 접점 시크릿 (MUL-87). 나가는 모든 요청 헤더에 붙는다.
 *                       비어 있으면 안 붙인다 — 로컬에서 도매도 검사를 생략하므로 짝이 맞는다
 */
@ConfigurationProperties(prefix = "ondo.wholesale")
public record WholesaleProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String secret) {

    public WholesaleProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);

        // HTTP 헤더 값은 ASCII 만 담는다. 한글이 섞이면 헤더가 깨져서 도매가 401 이
        // 아니라 400 을 주고, "시크릿이 틀렸나" 를 한참 헤매게 된다.
        // 기동할 때 바로 알려주는 편이 낫다
        if (secret != null && !secret.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
            throw new IllegalArgumentException(
                    "ondo.wholesale.secret 은 ASCII 로만 적는다. HTTP 헤더에 실리는 값이다");
        }
    }
}
