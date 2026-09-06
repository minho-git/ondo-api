package com.ondo.retail.order;

import com.ondo.retail.order.domain.OrderGroup;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderGroupRepository extends JpaRepository<OrderGroup, Long> {

    /** 멱등키로 찾는다. 같은 열쇠로 다시 오면 새로 만들지 않고 이걸 이어 쓴다. */
    Optional<OrderGroup> findByRequestId(String requestId);

    /**
     * 주문 내역.
     *
     * <p>{@code FAILED} 는 뺀다. 도매처가 전부 거절해 주문이 하나도 안 달린 껍데기라
     * 사용자에게는 만들어지지 않은 것으로 보여야 한다 (MUL-110 · V4).
     *
     * <p>기간은 <b>시각</b>으로 받고 <b>null 을 안 받는다.</b> 날짜 → 시각 변환도, 안 준
     * 기간을 어디까지로 볼지도 서비스가 정한다.
     *
     * <p>{@code :from IS NULL} 로 쓰지 않는 이유가 있다. 그러면 포스트그레스가
     * "이 파라미터가 무슨 타입이냐" 를 못 정해서 통째로 실패한다.
     *
     * <pre>ERROR: could not determine data type of parameter $2</pre>
     *
     * 값을 넣어 비교하는 자리가 하나도 없어서 생기는 일이다. 서비스가 넓은 경계를
     * 넣어주면 이 문제가 없고 쿼리도 한 갈래로 남는다.
     */
    @Query("""
            SELECT g FROM OrderGroup g
            WHERE g.retailerId = :retailerId
              AND g.status = com.ondo.retail.order.domain.OrderGroupStatus.ACCEPTED
              AND g.orderedAt >= :from
              AND g.orderedAt <  :to
            ORDER BY g.orderedAt DESC
            """)
    Page<OrderGroup> findAccepted(@Param("retailerId") Long retailerId,
                                  @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to,
                                  Pageable pageable);
}
