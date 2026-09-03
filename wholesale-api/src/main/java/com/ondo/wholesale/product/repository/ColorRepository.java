package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Color;
import org.springframework.data.jpa.repository.JpaRepository;

/** 색상 마스터 조회 전용. */
public interface ColorRepository extends JpaRepository<Color, Long> {
}
