package com.ondo.wholesale.config;

import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * 도매 API 보안 골격(MUL-66). 세션 인증(Spring Session JDBC) 기반.
 *
 * <p>이 커밋(3)은 1단 게이트만: 세션 없으면 401. 승인(APPROVED) 2단 게이트와 /me 예외는 커밋 4.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                // CSRF: MUL-45 인프라 협의 대기(SameSite vs 토큰). 협의 후 별도 활성화 — TODO
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // SecurityContext를 세션(→ Spring Session JDBC 테이블)에 저장/복원
                .securityContext(context -> context.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                // 로그인·로그아웃은 MUL-69. 기본 폼/베이직 로그인 비활성.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }
}
