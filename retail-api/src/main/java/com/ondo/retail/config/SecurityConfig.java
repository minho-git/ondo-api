package com.ondo.retail.config;

import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.common.error.ErrorResponse;
import com.ondo.retail.security.ApprovedAuthorizationManager;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
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
            // 헬스체크(MUL-76). ALB 가 부른다 — 401 이 나가면 배포가 영영 안 된다.
            // 하위까지 여는 건 ALB 가 실제로 보는 게 /actuator/health/liveness 여서다.
            "/actuator/health",
            "/actuator/health/**",
            // 프로메테우스 메트릭(MUL-117) — 로컬 모니터링(monitoring/)이 세션 없이 긁는다.
            // 배포는 이 엔드포인트 자체가 닫혀 있어(application.yml 이 health 만 연다)
            // 경로를 열어 둬도 404 만 나간다. 도매(MUL-115)와 같은 판단이다.
            "/actuator/prometheus"
    };

    /**
     * API 문서. 프론트가 붙기 전에 봐야 해서 연다.
     *
     * <p>⚠️ 실서비스 개시 전에는 닫거나 인증을 건다. 엔드포인트 목록과 요청 모양이
     * 그대로 드러나서, 공개해두면 공격면을 알려주는 셈이 된다.
     */
    private static final String[] DOCS_PATHS = {
            // 기본값 /v3/api-docs 에서 옮겼다 (MUL-110). 배포에서 도매와 같은 ALB 뒤에
            // 있는데 둘 다 기본 경로를 쓰고 있어서, 문서를 열면 한쪽만 보였다.
            // application.yml 의 springdoc.api-docs.path 와 같아야 한다
            "/v3/api-docs-retail",
            "/v3/api-docs-retail/**",
            "/docs",             // Scalar 화면
            "/docs/**"
    };

    /**
     * 로그인만 하면 되는 곳. 승인 전에도 열어둔다.
     *
     * <p>승인 대기 · 거절 화면이 자기 상태를 봐야 하고, 나가는 것도 돼야 한다.
     * 재신청과 비밀번호 변경은 추후 기능이라 아직 없다.
     */
    private static final String[] AUTHENTICATED_ONLY_PATHS = {
            "/api/retail/auth/me",
            "/api/retail/auth/logout"
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
                                           SecurityContextRepository securityContextRepository,
                                           ApprovedAuthorizationManager approvedAuthorizationManager) throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))

                // CORS(MUL-85). CorsConfig 의 소스를 쓴다. 인증 게이트보다 먼저 돌아야
                // 브라우저가 먼저 보내는 preflight(OPTIONS)가 401 로 튕기지 않는다 —
                // 그 요청엔 로그인 정보가 없어서 아래 규칙에 걸리면 본 요청이 시작도 못 한다.
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(DOCS_PATHS).permitAll()
                        .requestMatchers(AUTHENTICATED_ONLY_PATHS).authenticated()
                        // 나머지는 승인까지 돼야 한다. 앞으로 만드는 API 가 자동으로 걸린다
                        .anyRequest().access(approvedAuthorizationManager))

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
                                write(response, objectMapper, deniedCodeFor(request))))

                // 쿠키 인증이라 CSRF 방어가 필요하지만 방식이 아직 미정이다(숙제.md).
                // 프론트 배포 구성과 같이 정하기로 해서 지금은 꺼둔다.
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 거부 사유를 가른다.
     *
     * <p>승인 대기·거절이면 프론트가 승인 대기 화면으로 돌려야 해서 코드를 따로 준다.
     * 지금 소매에서 인증된 사용자가 막히는 경우는 미승인뿐이라 그렇게 본다.
     * 권한 종류가 늘면 여기서 갈라야 한다.
     */
    private ErrorCode deniedCodeFor(HttpServletRequest request) {
        return ErrorCode.ACCOUNT_NOT_APPROVED;
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
