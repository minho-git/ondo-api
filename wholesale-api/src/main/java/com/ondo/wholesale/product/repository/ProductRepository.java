package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
