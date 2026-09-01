package com.ondo.wholesale.security;

import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증됐으나 접근이 거부됐을 때(403). principal 상태로 두 403을 구분한다.
 *
 * <ul>
 *   <li>WholesalePrincipal 이고 승인상태 ≠ APPROVED → {@link ErrorCode#NOT_APPROVED}</li>
 *   <li>그 외(부적격 principal) → {@link ErrorCode#ACCESS_DENIED}</li>
 * </ul>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    public RestAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean notApproved = authentication != null
                && authentication.getPrincipal() instanceof WholesalePrincipal principal
                && principal.approvalStatus() != ApprovalStatus.APPROVED;
        errorResponseWriter.write(response, notApproved ? ErrorCode.NOT_APPROVED : ErrorCode.ACCESS_DENIED);
    }
}
