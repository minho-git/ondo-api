package com.ondo.wholesale.auth;

import com.ondo.wholesale.security.WholesalePrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 세션을 발급하고 무효화한다 (MUL-69).
 *
 * <p>"누구냐"를 정하는 건 {@code LoginService} 고, 여기는 그 결과를 <b>세션에 심는</b> 일만 한다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    /** 아무도 읽지 않는다. 게이트는 principal 타입과 승인 상태만 본다. 넣는 건 모양을 맞추기 위해서다. */
    private static final SimpleGrantedAuthority ROLE = new SimpleGrantedAuthority("ROLE_WHOLESALER");

    private final SecurityContextRepository securityContextRepository;

    /**
     * 로그인 성공을 세션에 심는다.
     *
     * <p>세 가지가 순서대로 필요하다.
     * <ol>
     *   <li><b>옛 세션을 버린다</b> — 컨트롤러에서 손으로 로그인하면 스프링 시큐리티의
     *       {@code SessionAuthenticationStrategy} 가 돌지 않아
     *       {@code sessionManagement().sessionFixation()} 설정이 이 경로엔 안 먹는다.
     *       직접 무효화하지 않으면 공격자가 미리 심어둔 세션 id 가 로그인 뒤에도 그대로 살아 있다.</li>
     *   <li><b>principal 은 {@link WholesalePrincipal} 그 자체</b> — 게이트
     *       ({@code ApprovedAuthorizationManager}·{@code RestAccessDeniedHandler})가
     *       {@code getPrincipal() instanceof WholesalePrincipal} 로 판단한다.
     *       id 문자열이나 {@code UserDetails} 를 넣으면 전부 접근 거부로 흘러간다.</li>
     *   <li><b>{@code saveContext} 를 직접 부른다</b> — 스프링 시큐리티 6 부터
     *       {@code requireExplicitSave} 가 기본이라 {@code SecurityContextHolder} 에 넣는
     *       것만으로는 세션에 아무것도 안 남는다. 증상은 "로그인은 200 인데 다음 요청이 401".</li>
     * </ol>
     *
     * <p>엔티티는 절대 넣지 않는다. 세션 속성은 {@code bytea} 자바 직렬화라
     * Hibernate 프록시·지연 컬렉션이 들어가면 깨진다.
     */
    public void authenticate(WholesalePrincipal principal,
                             HttpServletRequest request, HttpServletResponse response) {
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(ROLE));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        request.getSession(true);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * 세션을 버린다. 세션이 없으면 아무것도 하지 않는다 — 로그아웃은 204 로 멱등해야 한다.
     *
     * <p>세션을 무효화하면 {@code wholesale.spring_session} 의 행도 같이 사라지고,
     * {@code spring_session_attributes} 는 {@code ON DELETE CASCADE} 로 따라 지워진다.
     */
    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
