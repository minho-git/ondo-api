package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Variant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    /** 배분·배분취소(MUL-47)의 재고 경합 직렬화 — id 오름차순 잠금이라 교착이 없다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variant v where v.id in :ids order by v.id")
    List<Variant> lockAllByIdIn(@Param("ids") List<Long> ids);

    /** 살아있는 variant 만 — soft delete(D-053)된 행은 응답·검증 어디에도 안 나온다. */
    List<Variant> findByProductIdAndDeletedAtIsNull(Long productId);

    /** 목록 집계 — 삭제 variant 를 뺀 색 수·SKU 수를 상품별로 한 쿼리에. */
    @Query("""
            select new com.ondo.wholesale.product.repository.VariantCountRow(
                v.product.id, count(distinct v.colorOption.id), count(v))
            from Variant v
            where v.product.id in :productIds and v.deletedAt is null
            group by v.product.id
            """)
    List<VariantCountRow> countAliveByProductIds(@Param("productIds") List<Long> productIds);
}
