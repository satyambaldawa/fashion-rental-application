package com.fashionrental.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PackageComponentRepository extends JpaRepository<PackageComponent, UUID> {
    List<PackageComponent> findByPackageItem_Id(UUID packageItemId);
}
