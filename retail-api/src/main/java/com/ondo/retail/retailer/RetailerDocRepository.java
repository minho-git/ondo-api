package com.ondo.retail.retailer;

import com.ondo.retail.retailer.domain.RetailerDoc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailerDocRepository extends JpaRepository<RetailerDoc, Long> {
}
