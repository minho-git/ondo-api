package com.ondo.wholesale.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 테이블에 무엇이 적히는지 못박는다 (MUL-69).
 *
 * <p>Spring Session 은 세션을 저장할 때 {@code authentication.name} 을
 * {@code wholesale.spring_session.principal_name} 컬럼에 넣는다. 그 컬럼은
 * <b>{@code varchar(100)}</b> 이다(V1__init.sql).
 *
 * <p>그런데 {@code Authentication#getName()} 은 principal 이
 * {@code UserDetails}·{@code AuthenticatedPrincipal}·{@code Principal} 중 어느 것도 아니면
 * {@code toString()} 으로 떨어진다. record 의 기본 {@code toString()} 은
 * {@code WholesalePrincipal[wholesalerId=1, email=..., approvalStatus=APPROVED]} 라
 * 고정 부분만 70자가 넘는다. {@code wholesaler.email} 도 {@code varchar(100)} 이므로
 * <b>이메일이 길면 컬럼을 넘겨 로그인 직후 500 이 난다.</b>
 * 짧은 이메일로 개발할 땐 안 터지고 운영에서 터지는 종류다.
 */
class WholesalePrincipalNameTest {

    private static final WholesalePrincipal 도매처 =
            new WholesalePrincipal(42L, "owner@ondo.test", ApprovalStatus.APPROVED);

    /**
     * 이름은 도매처 id 다.
     *
     * <p>이메일이 아니라 id 인 이유는 두 가지다 — 계정을 정지시킬 때 그 사람 세션만
     * 찾아 지울 수 있고, 세션 테이블에 이메일을 평문으로 남기지 않는다.
     */
    @Test
    void 세션에_적히는_이름은_도매처_id_다() {
        Authentication 인증 = 인증(도매처);

        assertThat(인증.getName()).isEqualTo("42");
    }

    @Test
    void 이메일이_길어도_이름이_컬럼_길이를_넘지_않는다() {
        String 긴_이메일 = "a".repeat(88) + "@ondo.test";   // 98자 — email 컬럼(varchar 100) 상한 근처
        Authentication 인증 = 인증(new WholesalePrincipal(9999L, 긴_이메일, ApprovalStatus.PENDING));

        assertThat(인증.getName().length())
                .as("spring_session.principal_name 이 varchar(100) 이라 넘기면 로그인 직후 500 이 난다")
                .isLessThanOrEqualTo(100);
    }

    private Authentication 인증(WholesalePrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}
