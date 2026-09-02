package com.ondo.wholesale.config;

import com.ondo.wholesale.security.ApprovalStatus;
import com.ondo.wholesale.support.PostgresTestSupport;
import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 시드 계정 (MUL-62) — "클론 → bootRun → 바로 로그인"의 전제를 고정한다.
 * 시더는 테스트 공통 설정이 꺼두므로, 이 테스트만 속성으로 다시 켠 전용 컨텍스트에서 검증한다.
 */
@SpringBootTest(properties = "ondo.seed.dev-account=true")
class LocalDevAccountSeederTest extends PostgresTestSupport {

    @Autowired
    private WholesalerRepository wholesalerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 개발계정이_승인상태로_심어지고_안내된_비밀번호로_로그인_가능하다() {
        Wholesaler dev = wholesalerRepository.findByEmail(LocalDevAccountSeeder.DEV_EMAIL).orElseThrow();

        assertThat(dev.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(passwordEncoder.matches(LocalDevAccountSeeder.DEV_PASSWORD, dev.getPasswordHash())).isTrue();
    }
}
