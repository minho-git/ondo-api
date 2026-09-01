package com.ondo.wholesale.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 (MUL-69).
 *
 * <p><b>형식만</b> 본다 — 값이 들어 있는지까지다. 여기서 걸리면 {@code VALIDATION_FAILED}(400),
 * 자격 대조 실패는 서비스가 {@code LOGIN_FAILED}(401) 로 낸다.
 *
 * <p>이메일에 {@code @Email} 을 <b>일부러 붙이지 않는다.</b> 붙이면 {@code "nope"} 는 400,
 * {@code "nope@ondo.test"} 는 401 로 갈린다. 그러면 밖에서 형식만 바꿔 넣어보며 응답이
 * 달라지는 걸 관찰할 수 있고, 티켓이 못박은 "실패는 단일 코드" 취지가 흐려진다.
 *
 * <p>비밀번호 정책(8~20자·영문·숫자·특수문자)도 보지 않는다. 그건 가입(MUL-68)에서 볼 일이다.
 * 로그인에서 정책을 보면 정책이 바뀌었을 때 옛 비밀번호를 쓰던 사람이 로그인조차 못 한다.
 */
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password) {
}
