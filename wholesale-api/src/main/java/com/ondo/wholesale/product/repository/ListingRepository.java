package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /** 상품당 1건(listing_product_uk) — 살아있는 게시글. */
    Optional<Listing> findByProductIdAndDeletedAtIsNull(Long productId);

    /** 목록 배치 로딩용. */
    List<Listing> findByProductIdInAndDeletedAtIsNull(List<Long> productIds);
}
