package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.AllocationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationBatchRepository extends JpaRepository<AllocationBatch, Long> {
}
