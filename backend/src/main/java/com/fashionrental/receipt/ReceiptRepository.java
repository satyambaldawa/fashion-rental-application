package com.fashionrental.receipt;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    long countByReceiptNumberStartingWith(String prefix);

    @EntityGraph(Receipt.SUMMARY_ENTITY_GRAPH)
    List<Receipt> findByStatusOrderByEndDatetimeAsc(Receipt.Status status);

    @EntityGraph(Receipt.SUMMARY_ENTITY_GRAPH)
    List<Receipt> findByStatusAndEndDatetimeBeforeOrderByEndDatetimeAsc(Receipt.Status status, OffsetDateTime now);

    List<Receipt> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    List<Receipt> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);

    // Exclusive upper bound, unlike the BETWEEN above — for the one report where the range is
    // user-selected rather than a fixed calendar period, so two adjacent queries (e.g. June
    // then July) can't both count a receipt created exactly at the boundary instant.
    List<Receipt> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            OffsetDateTime from, OffsetDateTime to);

    java.util.Optional<Receipt> findByShareToken(String shareToken);
}
