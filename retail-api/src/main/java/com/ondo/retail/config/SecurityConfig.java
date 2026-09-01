package com.ondo.retail.config;

import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.common.error.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 어디를 열고 어디를 막을지 정한다.
 *
 * <p>이 파일이 없으면 스프링 시큐리티 기본값이 걸려서 모든 요청이 401 이다.
 * 로그인 API 조차 막혀서 로그인을 할 수가 없다.
 */
@Configuration
public class SecurityConfig {

    /** 로그인하지 않은 사람도 부를 수 있는 곳. */
    private static final String[] PUBLIC_PATHS = {
            "/api/retail/auth/sign-up",
            "/api/retail/auth/login",
            "/api/retail/auth/email-availability",
            "/actuator/health"
    };

    /**
     * 인증 정보를 세션에 넣고 꺼내는 곳. Spring Session 이 그 세션을 DB 로 보낸다.
     *
     * <p>로그인할 때 우리가 직접 {@code saveContext} 를 불러야 해서 빈으로 꺼내 뒀다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ObjectMapper objectMapper,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())

                // 세션은 쓰지만 스프링 시큐리티가 알아서 만들게 두지 않는다.
                // 로그인 성공 시 우리가 직접 만든다.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // 인증 실패한 요청을 세션에 저장하지 않는다.
                // 로그인 후 원래 주소로 돌려보내는 기능인데, JSON API 라 리다이렉트를 안 한다.
                // 켜두면 401 이 날 때마다 빈 세션이 DB 에 쌓인다.
                .requestCache(cache -> cache.disable())

                // 브라우저 기본 로그인 창과 로그인 폼을 끈다. 우리는 JSON API 다.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())

                // 인증 없이 보호된 곳을 부르면 우리 규약 모양으로 답한다.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                write(response, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                write(response, objectMapper, ErrorCode.FORBIDDEN)))

                // 쿠키 인증이라 CSRF 방어가 필요하지만 방식이 아직 미정이다(숙제.md).
                // 프론트 배포 구성과 같이 정하기로 해서 지금은 꺼둔다.
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void write(HttpServletResponse response, ObjectMapper mapper, ErrorCode code) {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            mapper.writeValue(response.getWriter(), ErrorResponse.of(code, null));
        } catch (Exception ignored) {
            // 응답을 쓰다 실패하면 더 할 수 있는 게 없다. status 는 이미 나갔다.
        }
    }
}
