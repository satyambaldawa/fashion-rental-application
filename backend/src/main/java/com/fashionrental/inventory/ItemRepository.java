package com.fashionrental.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

    @Query("SELECT DISTINCT i FROM Item i " +
           "LEFT JOIN FETCH i.photos " +
           "WHERE i.id = :id")
    Optional<Item> findByIdWithDetails(@Param("id") UUID id);

    Page<Item> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    // ORDER BY id is the global lock order: every transaction locking several items takes them in
    // the same sequence, so two checkouts can never deadlock on each other. No JOIN FETCH here:
    // PostgreSQL rejects FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id in :ids order by i.id")
    List<Item> lockAllByIdInIdOrder(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            SELECT COALESCE(SUM(rli.quantity), 0)
            FROM receipt_line_items rli
            JOIN receipts r ON rli.receipt_id = r.id
            WHERE rli.item_id = :itemId
            AND r.status IN ('GENERATED', 'GIVEN')
            """, nativeQuery = true)
    Integer countCurrentlyBookedUnits(@Param("itemId") UUID itemId);

    @Query(value = """
            SELECT COALESCE(SUM(rli.quantity), 0)
            FROM receipt_line_items rli
            JOIN receipts r ON rli.receipt_id = r.id
            WHERE rli.item_id = :itemId
            AND r.status IN ('GENERATED', 'GIVEN')
            AND r.start_datetime < :endDatetime
            AND r.end_datetime > :startDatetime
            """, nativeQuery = true)
    Integer countBookedUnits(
            @Param("itemId") UUID itemId,
            @Param("startDatetime") OffsetDateTime startDatetime,
            @Param("endDatetime") OffsetDateTime endDatetime
    );

    @Query(value = """
            SELECT rli.item_id, COALESCE(SUM(rli.quantity), 0)
            FROM receipt_line_items rli
            JOIN receipts r ON rli.receipt_id = r.id
            WHERE rli.item_id IN :itemIds
            AND r.status IN ('GENERATED', 'GIVEN')
            GROUP BY rli.item_id
            """, nativeQuery = true)
    List<Object[]> batchCountCurrentlyBookedUnits(@Param("itemIds") List<UUID> itemIds);

    @Query(value = """
            SELECT rli.item_id, COALESCE(SUM(rli.quantity), 0)
            FROM receipt_line_items rli
            JOIN receipts r ON rli.receipt_id = r.id
            WHERE rli.item_id IN :itemIds
            AND r.status IN ('GENERATED', 'GIVEN')
            AND r.start_datetime < :endDatetime
            AND r.end_datetime > :startDatetime
            GROUP BY rli.item_id
            """, nativeQuery = true)
    List<Object[]> batchCountBookedUnits(
            @Param("itemIds") List<UUID> itemIds,
            @Param("startDatetime") OffsetDateTime startDatetime,
            @Param("endDatetime") OffsetDateTime endDatetime
    );
}
