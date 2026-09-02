package com.ondo.wholesale.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 설정 (MUL-86). 프론트 dev 서버(localhost:3000)가 도매 API(8081)를 직접 부를 수 있게 연다.
 *
 * <p>허용 origin 은 {@code ondo.cors.allowed-origins} 프로퍼티로 주입한다 — 로컬은
 * application-local.yml, 배포는 배포 프로파일이 실제 프론트 도메인을 넣는다.
 * <b>미설정이면 허용 origin 이 없어 교차 출처 요청이 전부 막힌다</b>(안전한 기본값).
 *
 * <p>세션 쿠키 인증이라 {@code allowCredentials: true}가 필수고, 그 대가로 origin 을
 * {@code *} 로 못 쓴다 — 브라우저가 자격증명 요청에 와일드카드를 거부한다.
 * 쿠키 자체는 SameSite 판정이 포트를 안 보므로 localhost 끼리는 그대로 실린다.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${ondo.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        // Content-Type(JSON body) + 입고·입금의 멱등키. 넓힐 일이 생기면 여기에 추가한다
        config.setAllowedHeaders(List.of("Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
