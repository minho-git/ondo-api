package com.ondo.wholesale.security.support;

import com.ondo.wholesale.security.ApprovalStatus;
import com.ondo.wholesale.security.WholesalePrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 게이트 테스트용 principal 주입 헬퍼(테스트 소스).
 * 로그인이 세션에 신분증을 넣은 것처럼, 인증된 {@link WholesalePrincipal}을 SecurityContext에 심는다.
 */
public final class TestSecuritySupport {

    private TestSecuritySupport() {
    }

    /** 승인된 도매처 세션. */
    public static RequestPostProcessor approved() {
        return authentication(wholesaler(ApprovalStatus.APPROVED));
    }

    /** 승인 대기(PENDING) 도매처 세션. */
    public static RequestPostProcessor pending() {
        return authentication(wholesaler(ApprovalStatus.PENDING));
    }

    /** WholesalePrincipal이 아닌 인증(일반 권한부족 케이스 검증용). */
    public static RequestPostProcessor otherPrincipal() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "someone", null, List.of(new SimpleGrantedAuthority("ROLE_OTHER"))));
    }

    private static Authentication wholesaler(ApprovalStatus status) {
        WholesalePrincipal principal = new WholesalePrincipal(1L, "buyer@example.com", status);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_WHOLESALER")));
    }
}
