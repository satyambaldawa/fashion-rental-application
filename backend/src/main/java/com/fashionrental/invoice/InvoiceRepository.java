package com.fashionrental.invoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    long countByInvoiceNumberStartingWith(String prefix);

    List<Invoice> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);
}
