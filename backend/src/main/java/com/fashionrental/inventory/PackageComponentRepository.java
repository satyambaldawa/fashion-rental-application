package com.fashionrental.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackageComponentRepository extends JpaRepository<PackageComponent, UUID> {
    List<PackageComponent> findByPackageItem_Id(UUID packageItemId);

    @Query("select pc.componentItem.id from PackageComponent pc where pc.packageItem.id in :packageIds")
    List<UUID> findComponentItemIdsByPackageItemIds(@Param("packageIds") Collection<UUID> packageIds);

    List<PackageComponent> findByComponentItem_IdAndPackageItem_IsActiveTrue(UUID componentItemId);
}
