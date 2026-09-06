package com.ondo.wholesale.outbound.repository;

import com.ondo.wholesale.outbound.domain.Outbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface OutboundRepository extends JpaRepository<Outbound, Long>, JpaSpecificationExecutor<Outbound> {

    Optional<Outbound> findByIdAndWholesalerId(Long id, Long wholesalerId);
}
