package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /** 상품당 1건(listing_product_uk) — 살아있는 게시글. */
    Optional<Listing> findByProductIdAndDeletedAtIsNull(Long productId);

    /** 목록 배치 로딩용. */
    List<Listing> findByProductIdInAndDeletedAtIsNull(List<Long> productIds);

    /** 시즌 전이용 — 게시글은 상품을 거쳐야 도매처를 알 수 있어 소유 스코핑을 join 으로 건다. */
    @Query("""
            select l from Listing l join fetch l.product p
            where l.id = :listingId and p.wholesalerId = :wholesalerId
              and l.deletedAt is null and p.deletedAt is null
            """)
    Optional<Listing> findOwnedById(@Param("listingId") Long listingId,
                                    @Param("wholesalerId") Long wholesalerId);
}
