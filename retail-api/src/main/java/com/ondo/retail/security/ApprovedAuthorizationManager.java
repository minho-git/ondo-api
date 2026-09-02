package com.ondo.retail.security;

import com.ondo.retail.retailer.RetailerRepository;
import com.ondo.retail.retailer.domain.Retailer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * 승인된 소매처만 통과시킨다. 컨트롤러에 닿기 전에 걸러서 API 마다 검사를 안 쓴다.
 *
 * <p><b>매 요청 DB 를 본다.</b> 로그인할 때 세션에 담아두면 운영자가 승인을 취소해도
 * 그 세션이 만료될 때까지 계속 쓸 수 있다. 명세가 매 요청 {@code approval_status} 를
 * 보라고 한 이유이고, JWT 를 접은 이유와 같은 얘기다 — 즉시 끊을 수 있어야 한다.
 *
 * <p>조회가 한 번 늘지만 사용자가 동대문 도매·소매 사업자라 수가 적어 부담이 아니다.
 *
 * <p>거부하면 프레임워크가 나눠 처리한다 — 로그인 안 했으면 401, 로그인했는데 승인이
 * 안 됐으면 403 이다.
 */
@Component
@RequiredArgsConstructor
public class ApprovedAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private final RetailerRepository retailerRepository;

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        return new AuthorizationDecision(retailerIdOf(auth)
                .flatMap(retailerRepository::findById)
                .map(Retailer::isApproved)
                .orElse(false));
    }

    /**
     * 로그인할 때 principal 이름에 retailerId 를 넣어뒀다.
     * 익명 사용자는 "anonymousUser" 라 숫자가 아니다.
     */
    private static java.util.Optional<Long> retailerIdOf(Authentication auth) {
        try {
            return java.util.Optional.of(Long.valueOf(auth.getName()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
