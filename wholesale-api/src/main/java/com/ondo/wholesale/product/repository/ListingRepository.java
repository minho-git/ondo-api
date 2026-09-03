package com.ondo.wholesale.product.repository;

import com.ondo.wholesale.product.domain.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {
}
