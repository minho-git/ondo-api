package com.ondo.retail.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>이메일 형식까지는 보지 않는다. 형식이 틀렸다는 건 곧 없는 계정이라는 뜻인데,
 * 그걸 400 으로 알려주면 "이 이메일은 형식이 맞다/틀리다" 를 밖에서 떠보게 된다.
 * 값이 있는지만 보고 나머지는 401 로 묶는다.
 */
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해주세요")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        String password) {
}
