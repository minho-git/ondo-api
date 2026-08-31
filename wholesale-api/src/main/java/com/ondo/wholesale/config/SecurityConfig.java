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
                                    ApprovedAuthorizationManager approvedAuthorizationManager) throws Exception {
        http
                // CSRF: MUL-45 인프라 협의 대기(SameSite vs 토큰). 협의 후 별도 활성화 — TODO
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // SecurityContext를 세션(→ Spring Session JDBC 테이블)에 저장/복원
                .securityContext(context -> context.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/wholesale/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/wholesale/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/wholesale/me/reapply").authenticated()
                        .anyRequest().access(approvedAuthorizationManager))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 로그인·로그아웃은 MUL-69. 기본 폼/베이직 로그인 비활성.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    /** 공용 비밀번호 인코더. 스키마 password_hash가 BCrypt 확정 → 가입(MUL-68)·로그인(MUL-69)이 사용. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
