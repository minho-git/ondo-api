package com.ondo.wholesale.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 2단 게이트: 인증된 principal이 APPROVED 도매처일 때만 통과시킨다.
 *
 * <p>거부(deny) 시 처리는 프레임워크가 나눈다 — 미인증(익명)이면 401 EntryPoint,
 * 인증됐으나 미승인/부적격이면 403 AccessDeniedHandler로 넘어간다.
 */
@Component
public class ApprovedAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Authentication a = authentication.get();
        boolean approved = a != null
                && a.isAuthenticated()
                && a.getPrincipal() instanceof WholesalePrincipal principal
                && principal.approvalStatus() == ApprovalStatus.APPROVED;
        return new AuthorizationDecision(approved);
    }
}
