package com.ondo.wholesale.config;

import com.ondo.wholesale.wholesaler.Wholesaler;
import com.ondo.wholesale.wholesaler.WholesalerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 승인 계정 시더 (MUL-62).
 *
 * <p>클론 → bootRun 만으로 곧장 로그인할 수 있게, 앱이 뜰 때 승인 완료된 도매처
 * 계정 하나를 심는다: <b>dev@ondo.test / Ondo!2345</b>. 가입 → DB 승인 UPDATE 를
 * 손으로 치는 온보딩 단계를 없애는 것이 목적이다.
 *
 * <p>{@code @Profile("local")
@ConditionalOnProperty(name = "ondo.seed.dev-account", havingValue = "true", matchIfMissing = true)} — 기본 프로파일이 local 이라(application.yml) 로컬에선
 * 항상 돌고, 배포 프로파일에선 빈 자체가 로드되지 않는다. 마이그레이션이 아니라
 * 러너로 심는 이유: 시드가 스키마 이력에 섞이지 않고, 비밀번호를 {@link PasswordEncoder}
 * 로 인코딩하며, 엔티티를 거치므로 스키마가 바뀌면 컴파일 에러로 발각된다.
 *
 * <p>멱등 — 이메일로 찾아 이미 있으면 아무것도 하지 않는다. 재기동해도 중복이 안 생긴다.
 *
 * <p>테스트도 기본 프로파일(local)로 돌기 때문에 그대로 두면 시드가 테스트 데이터에 섞인다
 * (행 수 세는 테스트가 +1, 픽스처와 UNIQUE 충돌). 그래서 {@code ondo.seed.dev-account}
 * 속성으로도 잠갔고, 테스트 공통 설정(src/test/resources/application.properties)이 끈다.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "ondo.seed.dev-account", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalDevAccountSeeder implements ApplicationRunner {

    static final String DEV_EMAIL = "dev@ondo.test";
    static final String DEV_PASSWORD = "Ondo!2345";

    private final WholesalerRepository wholesalerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (wholesalerRepository.findByEmail(DEV_EMAIL).isPresent()) {
            return;
        }
        Wholesaler dev = Wholesaler.builder()
                .email(DEV_EMAIL)
                .passwordHash(passwordEncoder.encode(DEV_PASSWORD))
                .phone("01000000000")
                .bizRegNo("0000000000")
                .bizName("개발도매")
                .bizOwnerName("개발자")
                .bizCategory("개발용")
                .build();
        dev.approve();
        wholesalerRepository.save(dev);
        log.info("로컬 개발 계정 시드: {} (APPROVED)", DEV_EMAIL);
    }
}
