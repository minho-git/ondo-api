package com.ondo.wholesale.order.repository;

import com.ondo.wholesale.order.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
}
