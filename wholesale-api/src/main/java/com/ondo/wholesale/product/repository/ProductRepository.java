package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** 소유 스코핑 + soft delete 제외 — 타 도매처·삭제 상품은 404 로 흘린다. */
    Optional<Product> findByIdAndWholesalerIdAndDeletedAtIsNull(Long id, Long wholesalerId);
}
