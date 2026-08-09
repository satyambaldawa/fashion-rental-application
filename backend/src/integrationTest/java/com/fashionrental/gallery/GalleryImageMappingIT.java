package com.fashionrental.gallery;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.inventory.Item;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link GalleryImage} persistence against real PostgreSQL: guards the
 * @PrePersist timestamp population and that every {@link Item.Category} value round-trips
 * through the gallery_images.category column, which only a real database can catch.
 */
@Transactional
class GalleryImageMappingIT extends AbstractIntegrationTest {

    @Autowired
    private GalleryImageRepository galleryImageRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(Item.Category.class)
    void should_persist_gallery_image_for_every_category(Item.Category category) {
        GalleryImage image = newGalleryImage(category, "Caption for " + category);

        UUID id = galleryImageRepository.save(image).getId();
        entityManager.flush();
        entityManager.clear();

        GalleryImage loaded = galleryImageRepository.findById(id).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getCategory()).isEqualTo(category);
    }

    @Test
    void should_persist_gallery_image_with_null_caption() {
        GalleryImage image = newGalleryImage(Item.Category.COSTUME, null);

        UUID id = galleryImageRepository.save(image).getId();
        entityManager.flush();
        entityManager.clear();

        GalleryImage loaded = galleryImageRepository.findById(id).orElseThrow();

        assertThat(loaded.getCaption()).isNull();
    }

    private GalleryImage newGalleryImage(Item.Category category, String caption) {
        GalleryImage image = new GalleryImage();
        image.setCategory(category);
        image.setImageUrl("https://example.com/image.jpg");
        image.setThumbnailUrl("https://example.com/thumb.jpg");
        image.setCaption(caption);
        image.setSortOrder(0);
        image.setIsActive(true);
        return image;
    }
}
