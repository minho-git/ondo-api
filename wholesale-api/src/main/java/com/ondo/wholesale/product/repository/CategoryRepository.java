package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/** 카테고리 마스터 조회 전용. 소량 고정 데이터라 전건 로드 후 메모리에서 조립한다. */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
