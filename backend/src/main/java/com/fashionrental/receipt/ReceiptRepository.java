package com.fashionrental.receipt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    long countByReceiptNumberStartingWith(String prefix);

    // Closes the cancel-vs-return race: whichever transaction gets here first holds the row
    // lock until commit, so the loser re-reads the post-commit status and hits its guard.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findByIdForUpdate(@Param("id") UUID id);

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

    // Same inclusive BETWEEN convention as the daily/monthly created_at queries above —
    // the refund is attributed to the cancellation day, not the original booking day.
    List<Receipt> findByCancelledAtBetweenOrderByCancelledAtAsc(OffsetDateTime from, OffsetDateTime to);

    Optional<Receipt> findByShareToken(String shareToken);
}
