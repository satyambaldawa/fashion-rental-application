package com.fashionrental.configuration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    List<Coupon> findAllByOrderByCreatedAtDesc();

    List<Coupon> findByIsActiveTrueOrderByCreatedAtDesc();

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.timesUsed = c.timesUsed + 1 "
         + "WHERE c.id = :id AND c.isActive = true "
         + "AND (c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
    int incrementTimesUsed(@Param("id") UUID id);

    // A scalar projection, not findById: callers that reach here have already loaded the
    // Coupon earlier in the same transaction, so an entity lookup would be served from the
    // persistence context and return the stale read-time flag. Only a non-entity query
    // re-reads the committed row.
    @Query("SELECT c.isActive FROM Coupon c WHERE c.id = :id")
    Optional<Boolean> findIsActiveById(@Param("id") UUID id);
}
