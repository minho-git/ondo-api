package com.ondo.retail.order;

import com.ondo.retail.order.domain.OrderGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderGroupRepository extends JpaRepository<OrderGroup, Long> {

    /** 멱등키로 찾는다. 같은 열쇠로 다시 오면 새로 만들지 않고 이걸 이어 쓴다. */
    Optional<OrderGroup> findByRequestId(String requestId);
}
