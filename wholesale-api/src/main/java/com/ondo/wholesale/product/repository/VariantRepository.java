package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Variant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<Variant, Long> {
}
