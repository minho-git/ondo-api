package com.ondo.wholesale.wholesaler;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
