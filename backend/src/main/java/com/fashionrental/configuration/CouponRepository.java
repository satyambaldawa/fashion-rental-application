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
}
