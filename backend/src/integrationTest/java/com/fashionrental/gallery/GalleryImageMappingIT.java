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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link GalleryImage} persistence against real PostgreSQL: guards the
 * @PrePersist timestamp population, that every {@link Item.Category} value round-trips
 * through the gallery_images.category column, and that the newest-first ordering queries
 * behave — all things only a real database can catch.
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

    @Test
    void should_order_active_images_newest_first_and_exclude_inactive() {
        persistInOrder(
                newGalleryImage(Item.Category.COSTUME, "Oldest"),
                newGalleryImage(Item.Category.DRESS, "Middle"),
                newGalleryImage(Item.Category.DRESS, "Newest")
        );
        GalleryImage inactive = newGalleryImage(Item.Category.DRESS, "Inactive newest");
        inactive.setIsActive(false);
        persistInOrder(inactive);
        entityManager.clear();

        List<GalleryImage> result = galleryImageRepository.findByIsActiveTrueOrderByCreatedAtDescIdDesc();

        assertThat(result)
                .extracting(GalleryImage::getCaption)
                .containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    void should_return_only_active_images_for_requested_category_newest_first() {
        persistInOrder(
                newGalleryImage(Item.Category.PAGDI, "Older pagdi"),
                newGalleryImage(Item.Category.PAGDI, "Newer pagdi")
        );
        GalleryImage inactive = newGalleryImage(Item.Category.PAGDI, "Inactive pagdi");
        inactive.setIsActive(false);
        GalleryImage otherCategory = newGalleryImage(Item.Category.COSTUME, "Other category");
        persistInOrder(inactive, otherCategory);
        entityManager.clear();

        List<GalleryImage> result =
                galleryImageRepository.findByCategoryAndIsActiveTrueOrderByCreatedAtDescIdDesc(Item.Category.PAGDI);

        assertThat(result)
                .extracting(GalleryImage::getCaption)
                .containsExactly("Newer pagdi", "Older pagdi");
    }

    private void persistInOrder(GalleryImage... images) {
        for (GalleryImage image : images) {
            galleryImageRepository.saveAndFlush(image);
            sleepBriefly();
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private GalleryImage newGalleryImage(Item.Category category, String caption) {
        GalleryImage image = new GalleryImage();
        image.setCategory(category);
        image.setImageUrl("https://example.com/image.jpg");
        image.setThumbnailUrl("https://example.com/thumb.jpg");
        image.setCaption(caption);
        image.setIsActive(true);
        return image;
    }
}
