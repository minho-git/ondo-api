package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.ColorOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ColorOptionRepository extends JpaRepository<ColorOption, Long> {

    /** 상세 조립용 — 색·그룹까지 fetch join 으로 한 방에. 정렬은 조립기가 한다. */
    @Query("""
            select co from ColorOption co
            join fetch co.color c join fetch c.group
            where co.product.id = :productId
            """)
    List<ColorOption> findByProductIdWithColor(@Param("productId") Long productId);
}
