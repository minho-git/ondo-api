package com.ondo.retail.retailer;

import com.ondo.retail.retailer.domain.Retailer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailerRepository extends JpaRepository<Retailer, Long> {

    /**
     * 대소문자를 구분하지 않는다. 유니크 인덱스가 {@code lower(email)} 로 걸려 있어서
     * BomBom@naver.com 과 bombom@naver.com 은 같은 계정이다.
     */
    Optional<Retailer> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
