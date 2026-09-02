package com.ondo.wholesale.security;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;

/**
 * 세션에 담기는 인증 주체. Spring Session JDBC가 세션 속성을 자바 직렬화(스키마 attribute_bytes bytea)
 * 하므로 {@link Serializable} 이어야 한다.
 *
 * <p>이 골격(MUL-66)은 principal을 <b>읽어</b> 게이트를 태운다. 세션에 <b>쓰는</b> 쪽(로그인)은 MUL-69.
 *
 * <p>{@link AuthenticatedPrincipal} 을 구현하는 이유는 {@link #getName()} 주석 참고 (MUL-69).
 */
public record WholesalePrincipal(Long wholesalerId, String email, ApprovalStatus approvalStatus)
        implements Serializable, AuthenticatedPrincipal {

    private static final long serialVersionUID = 1L;

    /**
     * 세션 테이블의 {@code principal_name} 에 적히는 값. 이메일이 아니라 <b>도매처 id</b> 다.
     *
     * <p>Spring Session 이 {@code authentication.name} 을
     * {@code wholesale.spring_session.principal_name}({@code varchar(100)}) 에 넣는다.
     * 이 메서드가 없으면 {@code Authentication#getName()} 이 record 의 {@code toString()} 으로
     * 떨어져 <b>167자짜리 문자열</b>이 그 컬럼으로 향한다 — 이메일이 길면 로그인 직후 500 이 난다.
     *
     * <p>id 를 쓰는 이유는 또 있다. 계정을 정지시킬 때 그 사람 세션만 골라 지울 수 있고,
     * 세션 테이블에 이메일을 평문으로 남기지 않는다.
     */
    @Override
    public String getName() {
        return String.valueOf(wholesalerId);
    }
}
