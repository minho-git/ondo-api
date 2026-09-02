package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.LoginRequest;
import com.ondo.wholesale.auth.dto.LoginResponse;
import com.ondo.wholesale.security.WholesalePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인·로그아웃 엔드포인트 (MUL-69).
 *
 * <p>스프링 시큐리티의 로그인 필터를 쓰지 않고 컨트롤러에서 직접 한다. 필터로 하면
 * JSON 바디를 읽는 필터를 따로 써야 하고({@code UsernamePasswordAuthenticationFilter} 는
 * 폼 파라미터만 읽는다), 성공 응답의 {@code data} 봉투와 {@code @Valid} 검증이
 * 필터 단계에선 동작하지 않아 둘 다 손으로 다시 만들어야 한다.
 */
@Tag(name = "01 회원가입")
@RestController
@RequestMapping("/api/wholesale/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final SessionAuthenticator sessionAuthenticator;

    /**
     * 로그인. 성공하면 세션이 생기고 {@code SESSION_WHOLESALE} 쿠키가 붙는다.
     *
     * <p>승인되지 않은 계정도 200 이다. 프론트는 {@code approvalStatus} 를 보고
     * 진입 화면(대시보드 / 심사 대기 / 거절 안내)을 고른다.
     *
     * <p>{@link LoginResponse} 를 <b>그대로</b> 반환한다 — 봉투는
     * {@code ApiResponseBodyAdvice} 가 씌운다. 직접 감싸면 두 겹이 된다.
     */
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        WholesalePrincipal principal = loginService.authenticate(request);
        sessionAuthenticator.authenticate(principal, httpRequest, httpResponse);
        return new LoginResponse(principal.approvalStatus());
    }

    /**
     * 로그아웃. 세션이 없어도 204 다 — 만료된 쿠키로 눌러도 프론트가 따로 처리할 게 없다.
     *
     * <p>반환 타입이 {@code void} 라 반환값 처리기가 아예 돌지 않는다. 그래서
     * {@code ApiResponseBodyAdvice} 가 끼어들 여지가 없고 본문이 0바이트로 나간다.
     * {@code ApiResponse.of(null)} 을 반환하면 200 에 {@code {"data":null}} 이 나가버린다.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        sessionAuthenticator.clear(httpRequest);
    }
}
