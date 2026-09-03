package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.ListingVariant;
import com.ondo.wholesale.product.domain.ListingVariantId;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingVariantRepository extends JpaRepository<ListingVariant, ListingVariantId> {

    List<ListingVariant> findByIdListingId(Long listingId);
}
