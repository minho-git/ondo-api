package com.ondo.wholesale.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 품번 채번 (D-004: 도매처별 연번, 영구 결번).
 *
 * <p>UPDATE 가 wholesaler 행 락을 잡아 같은 도매처의 등록이 직렬화되므로
 * {@code product_number_uk} 충돌이 구조적으로 일어나지 않는다 — 커밋 시점
 * UNIQUE 예외를 따로 잡지 않는 근거다. Wholesaler 엔티티에 컬럼을 매핑하는 대신
 * SQL 한 줄로 두는 건, 이 카운터가 JPA 변경 감지를 타면 안 되는 값이기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ProductNumberAllocator {

    private final JdbcTemplate jdbc;

    public int next(Long wholesalerId) {
        return jdbc.queryForObject("""
                update wholesale.wholesaler
                   set last_product_seq = last_product_seq + 1, updated_at = now()
                 where id = ?
                returning last_product_seq
                """, Integer.class, wholesalerId);
    }
}
