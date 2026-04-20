package com.fashionrental.configuration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LateFeeRuleRepository extends JpaRepository<LateFeeRule, UUID> {

    List<LateFeeRule> findAllByOrderBySortOrderAsc();

    @Modifying
    @Query("UPDATE LateFeeRule r SET r.isActive = false")
    void deactivateAll();
}
