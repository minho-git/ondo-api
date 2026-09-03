package com.ondo.retail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 설정 (MUL-85). 소매 화면(localhost:3001 · ddmondo.co.kr)이 이 API 를 부를 수 있게 연다.
 *
 * <p>브라우저는 화면 주소와 API 주소가 다르면 응답을 화면에 안 넘긴다. 서버가 "이 주소는 허용"
 * 이라고 대답해줘야 넘긴다. 우리는 로컬(포트가 다름)도 배포(도메인이 다름)도 여기 걸린다.
 *
 * <p>⚠️ <b>서버를 지키는 장치가 아니다.</b> 검사는 브라우저가 한다 — curl 이나 다른 서버에서
 * 부르면 CORS 와 무관하게 그냥 된다. 서버를 지키는 건 세션 인증이고 그건 따로 계속 돈다.
 *
 * <p>허용 origin 은 {@code ondo.cors.allowed-origins} 로 주입한다 — 로컬은 application-local.yml,
 * 배포는 application-prod.yml. <b>미설정이면 허용 origin 이 없어 교차 출처가 전부 막힌다</b>(안전한 기본값).
 *
 * <p>세션 쿠키 인증이라 {@code allowCredentials: true} 가 필수고, 그 대가로 origin 에 {@code *} 를
 * 못 쓴다 — 브라우저가 자격증명 요청에 와일드카드를 거부한다. 그래서 주소를 하나하나 적는다.
 *
 * <p>도매(MUL-86)와 같은 모양으로 맞췄다. 서버 둘이 같은 방식이어야 배포 설정을 하나로 둔다.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${ondo.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        // Content-Type(JSON 본문 · 파일 업로드) + 주문 접수의 멱등키.
        // CSRF 를 켜면 X-XSRF-TOKEN 이 여기 붙는다(숙제 13번).
        config.setAllowedHeaders(List.of("Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
