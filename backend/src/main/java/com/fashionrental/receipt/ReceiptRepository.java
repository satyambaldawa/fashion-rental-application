package com.fashionrental.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    long countByReceiptNumberStartingWith(String prefix);

    List<Receipt> findByStatusOrderByEndDatetimeAsc(Receipt.Status status);

    List<Receipt> findByStatusAndEndDatetimeBeforeOrderByEndDatetimeAsc(Receipt.Status status, OffsetDateTime now);

    List<Receipt> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    List<Receipt> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);
}
