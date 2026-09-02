package com.ondo.wholesale.common.text;

import java.util.Locale;

/**
 * 이메일 정규화 한 곳.
 *
 * <p>가입(MUL-68)이 저장할 때와 로그인(MUL-69)이 대조할 때 <b>같은 규칙</b>을 써야 한다.
 * 규칙이 갈리면 가입한 계정으로 로그인이 안 되는데, 그 증상이 특정 입력에서만 나타나
 * 원인을 찾기 어렵다. 진입점은 앞으로도 늘어나므로(V3 마이그레이션 주석 참고) 한 곳에 둔다.
 *
 * <p>DB 쪽에도 같은 규칙이 걸려 있다 — {@code wholesaler_email_lower_ck} 가
 * {@code email = lower(email)} 을 강제한다. 여기를 건너뛴 값이 들어가면 그 제약에서 막힌다.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * 이메일을 소문자로 맞춘다.
     *
     * <p>{@link Locale#ROOT} 를 반드시 준다 — 터키어 로케일에서
     * {@code "I".toLowerCase()} 는 {@code "ı"}(점 없는 i)가 되어 서버 설정에 따라
     * 결과가 달라진다.
     */
    public static String normalize(String email) {
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
