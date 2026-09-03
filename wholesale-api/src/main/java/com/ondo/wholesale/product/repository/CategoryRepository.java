package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 카테고리 마스터 조회 전용. 소량 고정 데이터라 전건 로드 후 메모리에서 조립한다. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 트리 조립용 — 전역 정렬로 실어 오면 형제 그룹이 이미 정렬된 부분수열이라 앱 정렬이 필요 없다. */
    List<Category> findByActiveTrueOrderBySortOrderAscIdAsc();
}
