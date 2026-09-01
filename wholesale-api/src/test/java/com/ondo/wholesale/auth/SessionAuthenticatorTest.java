package com.ondo.wholesale.auth;

import com.ondo.wholesale.security.ApprovalStatus;
import com.ondo.wholesale.security.WholesalePrincipal;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 세션 발급·무효화 (MUL-69).
 *
 * <p>스프링 컨텍스트를 띄우지 않는다. 저장소는 <b>가짜가 아니라 진짜</b>
 * {@link HttpSessionSecurityContextRepository} 를 쓴다 — 여기서 확인할 게
 * "우리가 저장을 호출했는가"가 아니라 <b>다음 요청이 세션에서 복원할 수 있는가</b>라서다.
 */
class SessionAuthenticatorTest {

    private static final WholesalePrincipal 도매처 =
            new WholesalePrincipal(42L, "owner@ondo.test", ApprovalStatus.APPROVED);

    private final SecurityContextRepository 저장소 = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticator authenticator = new SessionAuthenticator(저장소);

    @AfterEach
    void 스레드에_남은_컨텍스트를_지운다() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 로그인 다음 요청이 세션에서 인증을 복원한다.
     *
     * <p>{@code SecurityContextHolder} 에 넣는 것만으로는 세션에 아무것도 안 남는다 —
     * 스프링 시큐리티 6부터 {@code requireExplicitSave} 가 기본이라 직접
     * {@code saveContext} 를 불러야 한다. 안 부르면 "로그인은 200 인데 다음 요청이 401"
     * 이라는 증상이 나온다.
     */
    @Test
    void 로그인하면_다음_요청이_세션에서_인증을_복원한다() {
        MockHttpServletRequest 로그인요청 = new MockHttpServletRequest();

        authenticator.authenticate(도매처, 로그인요청, new MockHttpServletResponse());

        MockHttpServletRequest 다음요청 = new MockHttpServletRequest();
        다음요청.setSession(로그인요청.getSession(false));
        SecurityContext 복원 = 저장소.loadDeferredContext(다음요청).get();

        assertThat(복원.getAuthentication()).isNotNull();
        assertThat(복원.getAuthentication().isAuthenticated()).isTrue();
        assertThat(복원.getAuthentication().getPrincipal())
                .as("게이트(ApprovedAuthorizationManager·RestAccessDeniedHandler)가 이 타입을 보고 판단한다")
                .isEqualTo(도매처);
    }

    /**
     * 로그인 전에 들고 온 세션은 버린다 (세션 고정 방어).
     *
     * <p>컨트롤러에서 손으로 로그인하면 스프링 시큐리티의
     * {@code SessionAuthenticationStrategy} 가 아예 돌지 않는다 —
     * {@code sessionManagement().sessionFixation()} 을 어떻게 설정해도 이 경로엔 안 먹는다.
     * 그래서 직접 무효화하고 새로 만든다. 안 그러면 공격자가 미리 심어둔 세션 id 가
     * 로그인 뒤에도 그대로 유효해진다.
     */
    @Test
    void 로그인_전에_들고온_세션은_버린다() {
        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        HttpSession 옛세션 = 요청.getSession(true);
        String 옛_세션_id = 옛세션.getId();
        옛세션.setAttribute("공격자가_심어둔_값", "x");

        authenticator.authenticate(도매처, 요청, new MockHttpServletResponse());

        HttpSession 새세션 = 요청.getSession(false);
        assertThat(새세션.getId()).isNotEqualTo(옛_세션_id);
        assertThat(새세션.getAttribute("공격자가_심어둔_값")).isNull();
    }

    @Test
    void 로그아웃하면_세션이_무효화된다() {
        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        authenticator.authenticate(도매처, 요청, new MockHttpServletResponse());
        MockHttpSession 세션 = (MockHttpSession) 요청.getSession(false);

        authenticator.clear(요청);

        assertThat(세션.isInvalid())
                .as("세션이 죽으면 wholesale.spring_session 의 행도 같이 사라진다")
                .isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 세션이 없어도 조용히 끝난다 — 로그아웃이 204 로 멱등해야 하기 때문이다. */
    @Test
    void 세션이_없어도_로그아웃은_조용히_끝난다() {
        assertThatNoException()
                .isThrownBy(() -> authenticator.clear(new MockHttpServletRequest()));
    }
}
