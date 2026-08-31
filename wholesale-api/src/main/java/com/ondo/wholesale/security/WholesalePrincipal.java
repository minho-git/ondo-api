package com.ondo.wholesale.security;

import java.io.Serializable;

/**
 * 세션에 담기는 인증 주체. Spring Session JDBC가 세션 속성을 자바 직렬화(스키마 attribute_bytes bytea)
 * 하므로 {@link Serializable} 이어야 한다.
 *
 * <p>이 골격(MUL-66)은 principal을 <b>읽어</b> 게이트를 태운다. 세션에 <b>쓰는</b> 쪽(로그인)은 MUL-69.
 */
public record WholesalePrincipal(Long wholesalerId, String email, ApprovalStatus approvalStatus)
        implements Serializable {

    private static final long serialVersionUID = 1L;
}
