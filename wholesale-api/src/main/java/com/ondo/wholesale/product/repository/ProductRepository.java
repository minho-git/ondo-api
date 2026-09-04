package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** 소유 스코핑 + soft delete 제외 — 타 도매처·삭제 상품은 404 로 흘린다. */
    Optional<Product> findByIdAndWholesalerIdAndDeletedAtIsNull(Long id, Long wholesalerId);

    /**
     * 수정·삭제용 잠금 조회. 상품 행을 잡으면 variant_seq 채번과
     * variant_size_uk·color_option_uk 경합이 함께 직렬화된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockByIdAndWholesalerIdAndDeletedAtIsNull(Long id, Long wholesalerId);
}
