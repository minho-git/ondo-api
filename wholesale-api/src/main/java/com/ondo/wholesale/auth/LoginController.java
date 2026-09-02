package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.LoginRequest;
import com.ondo.wholesale.auth.dto.LoginResponse;
import com.ondo.wholesale.security.WholesalePrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "로그인 (세션 발급)", description = """
            성공하면 세션이 생기고 `SESSION_WHOLESALE` 쿠키가 붙는다 — 토큰 필드는 없다.
            미승인 계정도 200이다: 프론트는 `approvalStatus`(PENDING/APPROVED/REJECTED)를 보고
            진입 화면을 고른다. 실패는 단일 코드 — 이메일 없음과 비밀번호 틀림을 구분하지 않는다(계정 열거 방지).

            에러: 400 `VALIDATION_FAILED`(빈 값) / 401 `LOGIN_FAILED`""")
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
    @Operation(summary = "로그아웃 (세션 무효화)", description = """
            항상 204, 본문 없음. 세션이 없거나 만료된 쿠키로 눌러도 204 — 프론트가 따로 처리할 게 없다.""")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        sessionAuthenticator.clear(httpRequest);
    }
}
