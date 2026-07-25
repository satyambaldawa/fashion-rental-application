package com.fashionrental.inventory;

import com.fashionrental.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ItemRepository#findByIdWithDetails} against real PostgreSQL.
 *
 * <p>Guards the MultipleBagFetchException regression: the fetch-join must load a package
 * item's photos AND components in one call without Hibernate rejecting multiple bag
 * fetches. A mock-based test cannot catch this because the query never runs.
 */
@Transactional
class ItemRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ItemRepository itemRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findByIdWithDetails_loads_photos_and_components_without_throwing() {
        Item component = itemRepository.save(newItem("Shivaji Costume", Item.ItemType.INDIVIDUAL));

        Item pkg = newItem("Shivaji Full Set", Item.ItemType.PACKAGE);
        addPhoto(pkg, "full.jpg", "thumb.jpg", 0);
        addComponent(pkg, component, 1);
        UUID packageId = itemRepository.save(pkg).getId();

        // Force the query to hit the database rather than the first-level cache.
        entityManager.flush();
        entityManager.clear();

        Item loaded = itemRepository.findByIdWithDetails(packageId).orElseThrow();

        assertThat(loaded.getPhotos()).hasSize(1);
        assertThat(loaded.getPackageComponents()).hasSize(1);
        assertThat(loaded.getPackageComponents().get(0).getComponentItem().getName())
                .isEqualTo("Shivaji Costume");
    }

    private Item newItem(String name, Item.ItemType type) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(type);
        item.setRate(250);
        item.setDeposit(200);
        item.setQuantity(1);
        return item;
    }

    private void addPhoto(Item item, String url, String thumbnailUrl, int sortOrder) {
        ItemPhoto photo = new ItemPhoto();
        photo.setItem(item);
        photo.setUrl(url);
        photo.setThumbnailUrl(thumbnailUrl);
        photo.setSortOrder(sortOrder);
        item.getPhotos().add(photo);
    }

    private void addComponent(Item pkg, Item component, int quantity) {
        PackageComponent pc = new PackageComponent();
        pc.setPackageItem(pkg);
        pc.setComponentItem(component);
        pc.setQuantity(quantity);
        pkg.getPackageComponents().add(pc);
    }
}
