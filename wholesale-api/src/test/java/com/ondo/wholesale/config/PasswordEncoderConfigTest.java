package com.ondo.wholesale.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공용 PasswordEncoder 빈 검증(후속 MUL-68 가입·MUL-69 로그인이 사용).
 */
class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void 평문을_BCrypt로_인코딩한다() {
        String hash = encoder.encode("secret1234");

        assertThat(hash).isNotEqualTo("secret1234");   // 평문 금지
        assertThat(hash).startsWith("$2");             // BCrypt 접두
    }

    @Test
    void 같은_평문은_matches로_대조된다() {
        String hash = encoder.encode("secret1234");

        assertThat(encoder.matches("secret1234", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
