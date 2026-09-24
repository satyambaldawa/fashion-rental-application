package com.fashionrental.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByStatus(Review.Status status, Pageable pageable);

    long countBySubmitterIpAndCreatedAtAfter(String submitterIp, OffsetDateTime since);

    long countByPhoneAndCreatedAtAfter(String phone, OffsetDateTime since);
}
