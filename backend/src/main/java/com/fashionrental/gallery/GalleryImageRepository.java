package com.fashionrental.gallery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {
}
