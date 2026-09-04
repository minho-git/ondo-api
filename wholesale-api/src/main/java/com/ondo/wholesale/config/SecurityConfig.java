package com.ondo.wholesale.config;

import com.ondo.wholesale.retailgateway.GatewaySecretAuthorizationManager;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 도매 API 보안 골격(MUL-66). 세션 인증(Spring Session JDBC) 기반의 2단 게이트.
 *
 * <ul>
 *   <li>{@code /api/wholesale/auth/**}(signup·login) → 비인증 허용</li>
 *   <li>{@code GET /me} · {@code POST /me/reapply} → 세션만(1단) 통과 — 심사 현황 확인용</li>
 *   <li>그 외 → 세션(1단) + APPROVED(2단) 모두 필요</li>
 *   <li>{@code /api/retail-gateway/**} → 세션이 아니라 시크릿 헤더로 확인 (MUL-87)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    RestAuthenticationEntryPoint authenticationEntryPoint,
                                    RestAccessDeniedHandler accessDeniedHandler,
                                    ApprovedAuthorizationManager approvedAuthorizationManager,
                                    GatewaySecretAuthorizationManager gatewaySecretAuthorizationManager,
                                    SecurityContextRepository securityContextRepository) throws Exception {
        http
                // CSRF: MUL-45 인프라 협의 대기(SameSite vs 토큰). 협의 후 별도 활성화 — TODO
                .csrf(csrf -> csrf.disable())
                // CORS(MUL-86): CorsConfig 의 소스를 쓴다. 인증 게이트보다 먼저 돌아야
                // 프론트 dev 서버의 preflight(OPTIONS)가 401 로 튕기지 않는다
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // SecurityContext를 세션(→ Spring Session JDBC 테이블)에 저장/복원
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        // 로그아웃도 여기 포함된다. 세션이 없어도 204 로 조용히 끝나야 해서다 —
                        // 인증을 요구하면 만료된 쿠키로 로그아웃할 때 프론트가 401 을 따로 처리해야 한다.
                        .requestMatchers("/api/wholesale/auth/**").permitAll()
                        // API 문서(MUL-81) — 로그인 전에 계약을 봐야 하는 프론트·소매 개발용
                        .requestMatchers("/docs.html", "/v3/api-docs/**").permitAll()
                        // 헬스체크(MUL-76) — ALB 가 부른다. 401 이 나가면 배포가 영영 안 된다.
                        // 하위까지 여는 건 ALB 가 실제로 보는 게 /actuator/health/liveness 여서다.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 소매 접점(MUL-82·87) — 부르는 게 사람이 아니라 소매 백엔드라
                        // 도매 세션이 없다. 양쪽이 나눠 가진 시크릿 헤더로 확인한다.
                        // 이 경로는 내부 ALB 로만 들어오지만 네트워크만 믿지는 않는다 —
                        // VPC 안이 뚫렸을 때 도매 데이터가 통째로 열리는 걸 막는다.
                        .requestMatchers("/api/retail-gateway/**").access(gatewaySecretAuthorizationManager)
                        .requestMatchers(HttpMethod.GET, "/api/wholesale/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/wholesale/me/reapply").authenticated()
                        .anyRequest().access(approvedAuthorizationManager))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 로그인·로그아웃은 LoginController 가 직접 한다. 기본 폼/베이직/로그아웃 필터는 끈다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    /**
     * 소매 접점의 시크릿 문지기 (MUL-87).
     *
     * <p>여기서 만드는 이유 — {@code @Component} 로 두면 {@code @Import(SecurityConfig.class)}
     * 하는 테스트들이 이 빈을 각자 적어줘야 한다. 보안 설정에 뭘 하나 더할 때마다
     * 남의 테스트가 같이 깨지는 건 좋지 않다.
     */
    @Bean
    GatewaySecretAuthorizationManager gatewaySecretAuthorizationManager(
            @Value("${ondo.gateway.secret:}") String secret) {
        return new GatewaySecretAuthorizationManager(secret);
    }

    /**
     * 인증 정보를 세션에 넣고 꺼내는 곳. Spring Session 이 그 세션을 DB 로 보낸다.
     *
     * <p>빈으로 꺼낸 이유는 로그인(MUL-69)이 직접 {@code saveContext} 를 불러야 해서다.
     * 필터 체인이 쓰는 것과 <b>같은 인스턴스</b>여야 로그인이 심은 인증을 다음 요청이 찾는다.
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 공용 비밀번호 인코더. 스키마 password_hash가 BCrypt 확정 → 가입(MUL-68)·로그인(MUL-69)이 사용. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
