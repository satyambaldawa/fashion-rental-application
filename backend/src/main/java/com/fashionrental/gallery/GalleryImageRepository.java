package com.fashionrental.gallery;

import com.fashionrental.inventory.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {

    List<GalleryImage> findByIsActiveTrueOrderByCreatedAtDescIdDesc();

    List<GalleryImage> findByCategoryAndIsActiveTrueOrderByCreatedAtDescIdDesc(Item.Category category);

    List<GalleryImage> findAllByOrderByCreatedAtDescIdDesc();

    List<GalleryImage> findByCategoryOrderByCreatedAtDescIdDesc(Item.Category category);
}
