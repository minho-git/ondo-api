package com.ondo.wholesale.outbound.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 출고번호 채번 (D-075: 도매처별 연번, 영구 결번) — ProductNumberAllocator 관행 복제.
 *
 * <p>UPDATE 가 wholesaler 행 락을 잡아 같은 도매처의 출고 생성이 직렬화되므로
 * {@code outbound_number_uk} 충돌이 구조적으로 일어나지 않는다. 락 순서 규약:
 * 이 락은 항상 주문 행 락(id 오름차순) 뒤, 트랜잭션의 마지막에 잡는다.
 */
@Component
@RequiredArgsConstructor
public class OutboundNumberAllocator {

    private final JdbcTemplate jdbc;

    public int next(Long wholesalerId) {
        return jdbc.queryForObject("""
                update wholesale.wholesaler
                   set last_outbound_seq = last_outbound_seq + 1, updated_at = now()
                 where id = ?
                returning last_outbound_seq
                """, Integer.class, wholesalerId);
    }
}
