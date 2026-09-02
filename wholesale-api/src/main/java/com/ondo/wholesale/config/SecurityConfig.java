package com.ondo.wholesale.config;

import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                                    SecurityContextRepository securityContextRepository) throws Exception {
        http
                // CSRF: MUL-45 인프라 협의 대기(SameSite vs 토큰). 협의 후 별도 활성화 — TODO
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // SecurityContext를 세션(→ Spring Session JDBC 테이블)에 저장/복원
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        // 로그아웃도 여기 포함된다. 세션이 없어도 204 로 조용히 끝나야 해서다 —
                        // 인증을 요구하면 만료된 쿠키로 로그아웃할 때 프론트가 401 을 따로 처리해야 한다.
                        .requestMatchers("/api/wholesale/auth/**").permitAll()
                        // API 문서(MUL-81) — 로그인 전에 계약을 봐야 하는 프론트·소매 개발용
                        .requestMatchers("/docs.html", "/v3/api-docs/**").permitAll()
                        // 소매 접점(MUL-82) — 인증 주체가 소매 백엔드라 도매 세션 밖.
                        // 인증 축이 미확정(U-14-B2: 서비스 간 토큰 vs 소매 사용자 토큰 검증)이라
                        // 결정 전까지 잠정 permitAll. 결정되면 전용 필터/매니저로 교체한다.
                        .requestMatchers("/api/retail-gateway/**").permitAll()
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
