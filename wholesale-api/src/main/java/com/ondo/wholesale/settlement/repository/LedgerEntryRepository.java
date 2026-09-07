package com.ondo.wholesale.settlement.repository;

import com.ondo.wholesale.settlement.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}
