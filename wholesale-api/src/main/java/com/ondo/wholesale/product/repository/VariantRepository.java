package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Variant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    /** 살아있는 variant 만 — soft delete(D-053)된 행은 응답·검증 어디에도 안 나온다. */
    List<Variant> findByProductIdAndDeletedAtIsNull(Long productId);
}
