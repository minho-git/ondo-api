package com.ondo.wholesale.auth;

import com.ondo.wholesale.auth.dto.SignupRequest;
import com.ondo.wholesale.auth.dto.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 신청 엔드포인트 (MUL-68).
 *
 * <p>비인증으로 연다 — {@code SecurityConfig} 가 {@code /api/wholesale/auth/**} 를
 * 이미 permitAll 로 열어뒀다. 세션도 주지 않는다. 가입 직후는 언제나 심사 대기라
 * 다시 들어올 때는 로그인 → 상태 조회 경로를 탄다.
 */
@RestController
@RequestMapping("/api/wholesale/auth")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    /**
     * 계정 · 사업자 정보 · 증빙 서류 · 동의를 한 번에 받아 심사 대기로 만든다.
     *
     * <p>{@code @Valid} 가 형식을 먼저 본다. 여기서 걸리면 서비스까지 오지 않고
     * {@code GlobalExceptionHandler} 가 400 {@code VALIDATION_FAILED} 로 돌려보낸다.
     *
     * <p>응답을 {@code SignupResponse} 그대로 돌려주면 {@code ApiResponseBodyAdvice} 가
     * {@code data} 봉투를 씌운다. 여기서 직접 감싸면 봉투가 두 겹이 된다.
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return signupService.signup(request);
    }
}
