package com.fashionrental.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    Page<Item> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    @Query(value = """
            SELECT COALESCE(SUM(rli.quantity), 0)
            FROM receipt_line_items rli
            JOIN receipts r ON rli.receipt_id = r.id
            WHERE rli.item_id = :itemId
            AND r.status = 'GIVEN'
            """, nativeQuery = true)
    Integer countCurrentlyBookedUnits(@Param("itemId") UUID itemId);

    @Query(value = """
            SELECT COALESCE(SUM(rli.quantity), 0)
            FROM receipt_line_items rli
            JOIN receipts r ON rli.receipt_id = r.id
            WHERE rli.item_id = :itemId
            AND r.status = 'GIVEN'
            AND r.start_datetime < :endDatetime
            AND r.end_datetime > :startDatetime
            """, nativeQuery = true)
    Integer countBookedUnits(
            @Param("itemId") UUID itemId,
            @Param("startDatetime") OffsetDateTime startDatetime,
            @Param("endDatetime") OffsetDateTime endDatetime
    );
}
