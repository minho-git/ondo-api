package com.ondo.wholesale.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 실패 에러 코드를 티켓대로 못박는다 (MUL-69).
 *
 * <p>티켓은 실패를 <b>{@code LOGIN_FAILED} 단일 코드</b>로 못박았다. 이메일이 없는 것과
 * 비밀번호가 틀린 것을 구분해 주면, 밖에서 이메일만 넣어보며 "이 사람이 가입했는지"를
 * 확인할 수 있게 된다(계정 열거). 그래서 둘을 하나로 묶는다.
 *
 * <p>이미 있는 {@link ErrorCode#UNAUTHENTICATED}(401) 로 대신하지 않는 이유는 <b>문구</b>다.
 * 그건 "세션이 없다"는 뜻이라 문구가 "인증이 필요합니다."인데, 로그인 폼 밑에 그 문구를
 * 띄울 수는 없다. 프론트가 둘을 code 로 갈라 볼 일은 없다 — 어느 엔드포인트를 불렀는지로
 * 이미 안다. 코드를 나누는 건 <b>사람이 볼 문구</b>와 <b>서버 로그·지표</b>(자격 실패인지
 * 만료 세션인지) 때문이다.
 *
 * <p>{@code code} 문자열이 응답에 {@code "LOGIN_FAILED"} 로 나가는지는 여기서 보지 않는다.
 * 이 저장소는 그걸 늘 실제 HTTP 응답에서 잡는다({@code AuthGateWebMvcTest},
 * {@code SignupApiTest}). 로그인도 {@code LoginApiTest} 에서 잡는다.
 */
class LoginErrorCodeTest {

    @Test
    void LOGIN_FAILED_는_401_이다() {
        assertThat(ErrorCode.LOGIN_FAILED.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 화면에_내보낼_문구가_비어_있지_않다() {
        assertThat(ErrorCode.LOGIN_FAILED.defaultMessage()).isNotBlank();
    }

    /**
     * 상태코드로 숨겨놓고 문구로 흘리는 걸 막는다.
     *
     * <p>"가입되지 않은 이메일입니다" 같은 문구를 내보내면 401 하나로 묶은 의미가 없어진다.
     * 키워드 몇 개만 보는 느슨한 그물이지만, 이 실수는 보통 저 단어들로 온다.
     */
    @Test
    void 어느_쪽이_틀렸는지_문구로도_흘리지_않는다() {
        String 문구 = ErrorCode.LOGIN_FAILED.defaultMessage();

        assertThat(문구)
                .as("'가입되지 않은 이메일' 같은 문구는 계정 존재 여부를 노출한다")
                .doesNotContain("가입")
                .doesNotContain("없는")
                .doesNotContain("존재");
    }
}
