package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.model.request.CreateItemRequest;
import com.fashionrental.inventory.model.request.PackageComponentRequest;
import com.fashionrental.inventory.model.response.ItemDetailResponse;
import com.fashionrental.inventory.model.response.ItemSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private com.fashionrental.inventory.PackageComponentRepository packageComponentRepository;

    @InjectMocks
    private ItemService itemService;

    private Item buildActiveItem(String name, Item.Category category, int rate, int deposit, int quantity) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(category);
        item.setRate(rate);
        item.setDeposit(deposit);
        item.setQuantity(quantity);
        item.setIsActive(true);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        return item;
    }

    private Item buildItemWithTimestamps(String name, Item.Category category, int rate, int deposit, int quantity, boolean active) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(category);
        item.setRate(rate);
        item.setDeposit(deposit);
        item.setQuantity(quantity);
        item.setIsActive(active);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        try {
            java.lang.reflect.Field createdAt = Item.class.getDeclaredField("createdAt");
            java.lang.reflect.Field updatedAt = Item.class.getDeclaredField("updatedAt");
            createdAt.setAccessible(true);
            updatedAt.setAccessible(true);
            createdAt.set(item, OffsetDateTime.now());
            updatedAt.set(item, OffsetDateTime.now());
        } catch (Exception ignored) {}
        return item;
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_empty_page_when_no_items() {
        Page<Item> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

        Page<ItemSummaryResponse> result = itemService.listItems(null, null, null, 0, 20, null, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_map_item_fields_correctly_in_list_response() {
        Item item = buildActiveItem("Blue Sherwani", Item.Category.COSTUME, 200, 1000, 3);
        item.setSize("M");
        Page<Item> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(availabilityService.getAvailableQuantity(any(), any(), any())).thenReturn(2);

        Page<ItemSummaryResponse> result = itemService.listItems(null, null, null, 0, 20, null, null);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ItemSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.name()).isEqualTo("Blue Sherwani");
        assertThat(summary.category()).isEqualTo(Item.Category.COSTUME);
        assertThat(summary.size()).isEqualTo("M");
        assertThat(summary.rate()).isEqualTo(200);
        assertThat(summary.deposit()).isEqualTo(1000);
        assertThat(summary.totalQuantity()).isEqualTo(3);
        assertThat(summary.availableQuantity()).isEqualTo(2);
        assertThat(summary.isAvailable()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_set_is_available_false_when_all_units_booked() {
        Item item = buildActiveItem("Red Lehenga", Item.Category.DRESS, 300, 2000, 2);
        Page<Item> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(availabilityService.getAvailableQuantity(any(), any(), any())).thenReturn(0);

        Page<ItemSummaryResponse> result = itemService.listItems(null, null, null, 0, 20, null, null);

        ItemSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.availableQuantity()).isZero();
        assertThat(summary.isAvailable()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_null_thumbnail_when_item_has_no_photos() {
        Item item = buildActiveItem("Gold Crown", Item.Category.ORNAMENTS, 100, 500, 1);
        // item.getPhotos() returns empty list by default
        Page<Item> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(availabilityService.getAvailableQuantity(any(), any(), any())).thenReturn(1);

        Page<ItemSummaryResponse> result = itemService.listItems(null, null, null, 0, 20, null, null);

        assertThat(result.getContent().get(0).thumbnailUrl()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return_first_photo_thumbnail_when_item_has_photos() {
        Item item = buildActiveItem("Silver Necklace", Item.Category.ORNAMENTS, 150, 800, 1);
        ItemPhoto photo1 = new ItemPhoto();
        photo1.setUrl("https://cdn.example.com/full1.jpg");
        photo1.setThumbnailUrl("https://cdn.example.com/thumb1.jpg");
        photo1.setSortOrder(0);
        photo1.setItem(item);
        ItemPhoto photo2 = new ItemPhoto();
        photo2.setUrl("https://cdn.example.com/full2.jpg");
        photo2.setThumbnailUrl("https://cdn.example.com/thumb2.jpg");
        photo2.setSortOrder(1);
        photo2.setItem(item);
        item.getPhotos().add(photo1);
        item.getPhotos().add(photo2);

        Page<Item> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(availabilityService.getAvailableQuantity(any(), any(), any())).thenReturn(1);

        Page<ItemSummaryResponse> result = itemService.listItems(null, null, null, 0, 20, null, null);

        assertThat(result.getContent().get(0).thumbnailUrl()).isEqualTo("https://cdn.example.com/thumb1.jpg");
    }

    @Test
    void should_create_item_with_correct_fields() {
        CreateItemRequest request = new CreateItemRequest(
                "Maharaja Costume",
                Item.Category.COSTUME,
                Item.ItemType.INDIVIDUAL,
                "L",
                "A royal costume",
                500,
                1000,
                3,
                "Handle with care",
                null,
                null,
                null
        );

        Item savedItem = buildActiveItem("Maharaja Costume", Item.Category.COSTUME, 500, 1000, 3);
        savedItem.setSize("L");
        savedItem.setDescription("A royal costume");
        savedItem.setNotes("Handle with care");

        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item item = inv.getArgument(0);
            // Simulate @PrePersist
            java.lang.reflect.Field createdAt;
            java.lang.reflect.Field updatedAt;
            try {
                createdAt = Item.class.getDeclaredField("createdAt");
                updatedAt = Item.class.getDeclaredField("updatedAt");
                createdAt.setAccessible(true);
                updatedAt.setAccessible(true);
                createdAt.set(item, OffsetDateTime.now());
                updatedAt.set(item, OffsetDateTime.now());
            } catch (Exception ignored) {}
            return item;
        });

        ItemDetailResponse result = itemService.createItem(request);

        assertThat(result.name()).isEqualTo("Maharaja Costume");
        assertThat(result.category()).isEqualTo(Item.Category.COSTUME);
        assertThat(result.rate()).isEqualTo(500);
        assertThat(result.deposit()).isEqualTo(1000);
        assertThat(result.quantity()).isEqualTo(3);
        assertThat(result.size()).isEqualTo("L");
    }

    @Test
    void should_create_item_with_is_active_true_by_default() {
        CreateItemRequest request = new CreateItemRequest(
                "New Item", Item.Category.ACCESSORIES, Item.ItemType.INDIVIDUAL, null, null, 100, 0, 1, null, null, null, null
        );
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item item = inv.getArgument(0);
            try {
                java.lang.reflect.Field createdAt = Item.class.getDeclaredField("createdAt");
                java.lang.reflect.Field updatedAt = Item.class.getDeclaredField("updatedAt");
                createdAt.setAccessible(true);
                updatedAt.setAccessible(true);
                createdAt.set(item, OffsetDateTime.now());
                updatedAt.set(item, OffsetDateTime.now());
            } catch (Exception ignored) {}
            return item;
        });

        ItemDetailResponse result = itemService.createItem(request);

        assertThat(result.isActive()).isTrue();
    }

    @Test
    void should_create_item_with_zero_deposit_allowed() {
        CreateItemRequest request = new CreateItemRequest(
                "Simple Pagdi", Item.Category.PAGDI, Item.ItemType.INDIVIDUAL, null, null, 50, 0, 2, null, null, null, null
        );
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item item = inv.getArgument(0);
            try {
                java.lang.reflect.Field createdAt = Item.class.getDeclaredField("createdAt");
                java.lang.reflect.Field updatedAt = Item.class.getDeclaredField("updatedAt");
                createdAt.setAccessible(true);
                updatedAt.setAccessible(true);
                createdAt.set(item, OffsetDateTime.now());
                updatedAt.set(item, OffsetDateTime.now());
            } catch (Exception ignored) {}
            return item;
        });

        ItemDetailResponse result = itemService.createItem(request);

        assertThat(result.deposit()).isZero();
    }

    @Test
    void should_throw_not_found_when_item_does_not_exist() {
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getItem(itemId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Package creation ──────────────────────────────────────────────────────

    @Test
    void should_throw_validation_when_package_has_no_components() {
        CreateItemRequest request = new CreateItemRequest(
                "Empty Package", Item.Category.COSTUME, Item.ItemType.PACKAGE,
                null, null, 500, 2000, 2, null, null, null,
                null
        );

        assertThatThrownBy(() -> itemService.createItem(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least one component");
    }

    @Test
    void should_throw_validation_when_package_component_is_itself_a_package() {
        UUID componentId = UUID.randomUUID();
        CreateItemRequest request = new CreateItemRequest(
                "Nested Package", Item.Category.COSTUME, Item.ItemType.PACKAGE,
                null, null, 500, 2000, 1, null, null, null,
                List.of(new PackageComponentRequest(componentId, 1))
        );

        Item anotherPackage = buildActiveItem("Other Package", Item.Category.COSTUME, 300, 1000, 2);
        anotherPackage.setItemType(Item.ItemType.PACKAGE);

        when(itemRepository.findById(componentId)).thenReturn(Optional.of(anotherPackage));

        assertThatThrownBy(() -> itemService.createItem(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("INDIVIDUAL items can be package components");
    }

    @Test
    void should_throw_not_found_when_package_component_does_not_exist() {
        UUID missingId = UUID.randomUUID();
        CreateItemRequest request = new CreateItemRequest(
                "Set", Item.Category.COSTUME, Item.ItemType.PACKAGE,
                null, null, 500, 2000, 1, null, null, null,
                List.of(new PackageComponentRequest(missingId, 1))
        );

        when(itemRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.createItem(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Component item not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_include_component_names_in_package_summary() {
        Item componentItem = buildActiveItem("Pagdi", Item.Category.PAGDI, 50, 200, 5);

        PackageComponent pc = new PackageComponent();
        pc.setComponentItem(componentItem);
        pc.setQuantity(2);

        Item packageItem = buildActiveItem("Maharaja Set", Item.Category.COSTUME, 500, 2000, 3);
        packageItem.setItemType(Item.ItemType.PACKAGE);
        packageItem.getPackageComponents().add(pc);
        pc.setPackageItem(packageItem);

        Page<Item> page = new PageImpl<>(List.of(packageItem), PageRequest.of(0, 20), 1);
        when(itemRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(availabilityService.getAvailableQuantity(any(), any(), any())).thenReturn(3);

        Page<ItemSummaryResponse> result = itemService.listItems(null, null, null, 0, 20, null, null);

        ItemSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.itemType()).isEqualTo(Item.ItemType.PACKAGE);
        assertThat(summary.componentNames()).containsExactly("Pagdi ×2");
    }

    @Test
    void should_throw_not_found_when_item_is_inactive() {
        UUID itemId = UUID.randomUUID();
        Item inactiveItem = buildItemWithTimestamps("Old Costume", Item.Category.COSTUME, 100, 500, 1, false);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(inactiveItem));

        assertThatThrownBy(() -> itemService.getItem(itemId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(itemId.toString());
    }

    @Test
    void should_return_item_detail_with_all_fields_when_active() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItemWithTimestamps("Golden Sherwani", Item.Category.COSTUME, 500, 2000, 3, true);
        item.setSize("XL");
        item.setDescription("Grand golden sherwani");
        item.setNotes("Dry clean only");
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        ItemDetailResponse result = itemService.getItem(itemId);

        assertThat(result.name()).isEqualTo("Golden Sherwani");
        assertThat(result.category()).isEqualTo(Item.Category.COSTUME);
        assertThat(result.size()).isEqualTo("XL");
        assertThat(result.description()).isEqualTo("Grand golden sherwani");
        assertThat(result.notes()).isEqualTo("Dry clean only");
        assertThat(result.rate()).isEqualTo(500);
        assertThat(result.deposit()).isEqualTo(2000);
        assertThat(result.quantity()).isEqualTo(3);
        assertThat(result.isActive()).isTrue();
        assertThat(result.photos()).isEmpty();
    }

    @Test
    void should_include_photos_in_item_detail_response() {
        UUID itemId = UUID.randomUUID();
        Item item = buildItemWithTimestamps("Silk Saree", Item.Category.DRESS, 400, 1500, 2, true);

        ItemPhoto photo = new ItemPhoto();
        photo.setUrl("https://cdn.example.com/full.jpg");
        photo.setThumbnailUrl("https://cdn.example.com/thumb.jpg");
        photo.setSortOrder(0);
        photo.setItem(item);
        item.getPhotos().add(photo);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        ItemDetailResponse result = itemService.getItem(itemId);

        assertThat(result.photos()).hasSize(1);
        assertThat(result.photos().get(0).url()).isEqualTo("https://cdn.example.com/full.jpg");
        assertThat(result.photos().get(0).thumbnailUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
        assertThat(result.photos().get(0).sortOrder()).isZero();
    }
}
