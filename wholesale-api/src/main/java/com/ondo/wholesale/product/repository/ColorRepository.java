package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Color;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** 색상 마스터 조회 전용. */
public interface ColorRepository extends JpaRepository<Color, Long> {

    /** 팔레트 순서(그룹 sort_order → 그룹 내 sort_order)로 그룹까지 한 방에. */
    @Query("""
            select c from Color c join fetch c.group g
            order by g.sortOrder, g.id, c.sortOrder, c.id
            """)
    List<Color> findAllWithGroupOrdered();
}
