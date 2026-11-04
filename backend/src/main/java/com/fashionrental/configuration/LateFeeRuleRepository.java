package com.fashionrental.configuration;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LateFeeRuleRepository extends JpaRepository<LateFeeRule, UUID> {

    List<LateFeeRule> findByIsActiveTrueOrderBySortOrderAsc();

    // Serialises concurrent "replace all rules" saves so the version check and the rewrite
    // that follows it see one consistent set.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LateFeeRule r")
    List<LateFeeRule> findAllForUpdate();
}
