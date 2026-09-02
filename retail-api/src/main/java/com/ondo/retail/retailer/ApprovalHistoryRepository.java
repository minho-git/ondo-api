package com.ondo.retail.retailer;

import com.ondo.retail.retailer.domain.ApprovalHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

    /**
     * 가장 최근 이력 한 줄. 거절 사유를 볼 때 쓴다.
     *
     * <p>여러 줄이 쌓여 있어도 <b>마지막 줄만</b> 본다 — 지금 상태를 만든 게 그 줄이다.
     * {@code approval_history_recent_idx} 가 {@code (retailer_id, created_at DESC)} 라 그대로 탄다.
     */
    Optional<ApprovalHistory> findTopByRetailerIdOrderByCreatedAtDesc(Long retailerId);
}
