package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 소유 스코핑 + soft delete 제외 — 타 도매처·삭제 상품은 404 로 흘린다. */
    Optional<Product> findByIdAndWholesalerIdAndDeletedAtIsNull(Long id, Long wholesalerId);
}
