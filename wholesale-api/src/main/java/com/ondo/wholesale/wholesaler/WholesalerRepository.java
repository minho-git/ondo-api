package com.ondo.wholesale.wholesaler;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WholesalerRepository extends JpaRepository<Wholesaler, Long> {

    /**
     * 가입 전 중복 확인. 정상 경로에서 친절한 에러 코드를 내려고 먼저 본다.
     *
     * <p>이것만으로는 부족하다 — 조회와 INSERT 사이에 다른 요청이 끼어들 수 있고,
     * 인스턴스가 여러 대면 실제로 일어난다. 최종 방어는 wholesaler_email_uk 다.
     *
     * <p>인자는 이미 소문자로 정규화된 값이어야 한다.
     */
    boolean existsByEmail(String email);

    /** 사업자등록번호 중복 확인. 최종 방어는 wholesaler_biz_reg_no_uk 다. */
    boolean existsByBizRegNo(String bizRegNo);

    /**
     * 로그인 자격 대조용 조회.
     *
     * <p>인자는 이미 소문자로 정규화된 값이어야 한다 — 컬럼에 wholesaler_email_lower_ck 가
     * 걸려 있어 저장된 값은 항상 소문자다. 그래서 함수 비교(upper/lower) 없이 그대로
     * 맞춰볼 수 있고 wholesaler_email_uk 인덱스를 탄다.
     */
    Optional<Wholesaler> findByEmail(String email);
}
