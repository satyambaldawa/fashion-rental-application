package com.fashionrental.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemPhotoRepository extends JpaRepository<ItemPhoto, UUID> {

    List<ItemPhoto> findByItemIdOrderBySortOrderAsc(UUID itemId);

    long countByItemId(UUID itemId);

    Optional<ItemPhoto> findByIdAndItemId(UUID id, UUID itemId);

    @Modifying
    @Query("UPDATE ItemPhoto p SET p.sortOrder = :sortOrder WHERE p.id = :id")
    void updateSortOrder(@Param("id") UUID id, @Param("sortOrder") int sortOrder);
}
