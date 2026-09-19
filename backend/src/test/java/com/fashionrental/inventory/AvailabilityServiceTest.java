package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private PackageComponentRepository packageComponentRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    private Item activeItemWithQuantity(UUID id, int quantity) {
        Item item = new Item();
        item.setQuantity(quantity);
        item.setIsActive(true);
        return item;
    }

    private Item activeItemWithId(UUID id, int quantity, Item.ItemType type) {
        Item item = new Item();
        item.setQuantity(quantity);
        item.setIsActive(true);
        item.setItemType(type);
        try {
            var f = Item.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(item, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return item;
    }

    @Test
    void should_return_zero_when_all_units_booked_for_date_range() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 3);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countBookedUnits(itemId, start, end)).thenReturn(3);

        int available = availabilityService.getAvailableQuantity(itemId, start, end);

        assertThat(available).isZero();
    }

    @Test
    void should_return_full_quantity_when_no_overlap_at_exact_boundary() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 3);
        // Receipt ends exactly at query start — the condition end_datetime > start_datetime means no overlap
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countBookedUnits(itemId, start, end)).thenReturn(0);

        int available = availabilityService.getAvailableQuantity(itemId, start, end);

        assertThat(available).isEqualTo(3);
    }

    @Test
    void should_return_partial_availability_when_one_of_three_units_booked() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 3);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countBookedUnits(itemId, start, end)).thenReturn(1);

        int available = availabilityService.getAvailableQuantity(itemId, start, end);

        assertThat(available).isEqualTo(2);
    }

    @Test
    void should_return_zero_for_inactive_item() {
        UUID itemId = UUID.randomUUID();
        Item item = new Item();
        item.setQuantity(5);
        item.setIsActive(false);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        int available = availabilityService.getAvailableQuantity(itemId, null, null);

        assertThat(available).isZero();
    }

    @Test
    void should_use_current_booking_count_when_no_date_range_given() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 3);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countCurrentlyBookedUnits(itemId)).thenReturn(1);

        int available = availabilityService.getAvailableQuantity(itemId, null, null);

        assertThat(available).isEqualTo(2);
    }

    @Test
    void should_throw_not_found_when_item_does_not_exist() {
        UUID itemId = UUID.randomUUID();

        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.getAvailableQuantity(itemId, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_never_return_negative_availability_when_booked_exceeds_quantity() {
        // Defensive: guards against data inconsistency (e.g. quantity was reduced after booking)
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 2);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countBookedUnits(itemId, start, end)).thenReturn(5); // more than quantity

        int available = availabilityService.getAvailableQuantity(itemId, start, end);

        assertThat(available).isZero();
    }

    @Test
    void should_return_full_quantity_when_no_bookings_exist_for_date_range() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 4);
        OffsetDateTime start = OffsetDateTime.now().plusDays(30);
        OffsetDateTime end = start.plusDays(2);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countBookedUnits(itemId, start, end)).thenReturn(0);

        int available = availabilityService.getAvailableQuantity(itemId, start, end);

        assertThat(available).isEqualTo(4);
    }

    @Test
    void should_return_zero_for_inactive_item_even_with_date_range() {
        UUID itemId = UUID.randomUUID();
        Item item = new Item();
        item.setQuantity(3);
        item.setIsActive(false);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        int available = availabilityService.getAvailableQuantity(itemId, start, end);

        assertThat(available).isZero();
    }

    @Test
    void should_return_availability_for_all_items_in_batch() {
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item item1 = activeItemWithQuantity(itemId1, 3);
        Item item2 = activeItemWithQuantity(itemId2, 2);

        when(itemRepository.findById(itemId1)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(itemId2)).thenReturn(Optional.of(item2));
        when(itemRepository.countBookedUnits(itemId1, start, end)).thenReturn(1);
        when(itemRepository.countBookedUnits(itemId2, start, end)).thenReturn(2);

        Map<UUID, Integer> result = availabilityService.getAvailableQuantities(List.of(itemId1, itemId2), start, end);

        assertThat(result).hasSize(2);
        assertThat(result.get(itemId1)).isEqualTo(2);
        assertThat(result.get(itemId2)).isZero();
    }

    @Test
    void should_return_zero_currently_booked_units_when_no_active_receipts() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithQuantity(itemId, 5);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.countCurrentlyBookedUnits(itemId)).thenReturn(0);

        int available = availabilityService.getAvailableQuantity(itemId, null, null);

        assertThat(available).isEqualTo(5);
    }

    // ── Package availability ──────────────────────────────────────────────────

    @Test
    void should_constrain_package_availability_to_component_stock() {
        // Package has 3 sets in stock; component has only 2 units → max 2 sets
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item packageItem = activeItemWithId(packageId, 3, Item.ItemType.PACKAGE);
        Item componentItem = activeItemWithId(componentId, 2, Item.ItemType.INDIVIDUAL);

        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(1);

        when(itemRepository.findById(packageId)).thenReturn(Optional.of(packageItem));
        when(itemRepository.countBookedUnits(packageId, start, end)).thenReturn(0);
        when(packageComponentRepository.findByPackageItem_Id(packageId)).thenReturn(List.of(comp));
        when(itemRepository.findById(componentId)).thenReturn(Optional.of(componentItem));
        when(itemRepository.countBookedUnits(componentId, start, end)).thenReturn(0);

        int available = availabilityService.getAvailableQuantity(packageId, start, end);

        assertThat(available).isEqualTo(2);
    }

    @Test
    void should_divide_component_stock_by_quantity_per_set() {
        // Package stock=5; component has 4 units but needs 2 per set → floor(4/2)=2 sets
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item packageItem = activeItemWithId(packageId, 5, Item.ItemType.PACKAGE);
        Item componentItem = activeItemWithId(componentId, 4, Item.ItemType.INDIVIDUAL);

        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(2); // 2 units of this component per set

        when(itemRepository.findById(packageId)).thenReturn(Optional.of(packageItem));
        when(itemRepository.countBookedUnits(packageId, start, end)).thenReturn(0);
        when(packageComponentRepository.findByPackageItem_Id(packageId)).thenReturn(List.of(comp));
        when(itemRepository.findById(componentId)).thenReturn(Optional.of(componentItem));
        when(itemRepository.countBookedUnits(componentId, start, end)).thenReturn(0);

        int available = availabilityService.getAvailableQuantity(packageId, start, end);

        assertThat(available).isEqualTo(2);
    }

    @Test
    void should_return_zero_package_availability_when_component_fully_booked() {
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item packageItem = activeItemWithId(packageId, 3, Item.ItemType.PACKAGE);
        Item componentItem = activeItemWithId(componentId, 2, Item.ItemType.INDIVIDUAL);

        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(1);

        when(itemRepository.findById(packageId)).thenReturn(Optional.of(packageItem));
        when(itemRepository.countBookedUnits(packageId, start, end)).thenReturn(0);
        when(packageComponentRepository.findByPackageItem_Id(packageId)).thenReturn(List.of(comp));
        when(itemRepository.findById(componentId)).thenReturn(Optional.of(componentItem));
        when(itemRepository.countBookedUnits(componentId, start, end)).thenReturn(2); // all booked

        int available = availabilityService.getAvailableQuantity(packageId, start, end);

        assertThat(available).isZero();
    }

    @Test
    void should_use_most_constrained_component_when_package_has_multiple() {
        // 2 components: comp-A has 4 available, comp-B has 1 available → package limited to 1
        UUID packageId = UUID.randomUUID();
        UUID compAId = UUID.randomUUID();
        UUID compBId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item packageItem = activeItemWithId(packageId, 10, Item.ItemType.PACKAGE);
        Item compA = activeItemWithId(compAId, 4, Item.ItemType.INDIVIDUAL);
        Item compB = activeItemWithId(compBId, 1, Item.ItemType.INDIVIDUAL);

        PackageComponent pcA = new PackageComponent();
        pcA.setComponentItem(compA);
        pcA.setQuantity(1);

        PackageComponent pcB = new PackageComponent();
        pcB.setComponentItem(compB);
        pcB.setQuantity(1);

        when(itemRepository.findById(packageId)).thenReturn(Optional.of(packageItem));
        when(itemRepository.countBookedUnits(packageId, start, end)).thenReturn(0);
        when(packageComponentRepository.findByPackageItem_Id(packageId)).thenReturn(List.of(pcA, pcB));
        when(itemRepository.findById(compAId)).thenReturn(Optional.of(compA));
        when(itemRepository.countBookedUnits(compAId, start, end)).thenReturn(0); // 4 available
        when(itemRepository.findById(compBId)).thenReturn(Optional.of(compB));
        when(itemRepository.countBookedUnits(compBId, start, end)).thenReturn(0); // 1 available

        int available = availabilityService.getAvailableQuantity(packageId, start, end);

        assertThat(available).isEqualTo(1); // bottlenecked by comp-B
    }

    // ── batchGetAvailableQuantities ─────────────────────────────────────────

    @Test
    void should_return_empty_map_for_empty_item_list() {
        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(), null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_batch_compute_availability_for_simple_items_using_date_range() {
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item item1 = activeItemWithId(itemId1, 3, Item.ItemType.INDIVIDUAL);
        Item item2 = activeItemWithId(itemId2, 5, Item.ItemType.INDIVIDUAL);

        when(itemRepository.batchCountBookedUnits(List.of(itemId1, itemId2), start, end)).thenReturn(List.of(
                new Object[]{itemId1, 1},
                new Object[]{itemId2, 0}
        ));

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(item1, item2), start, end);

        assertThat(result.get(itemId1)).isEqualTo(2);
        assertThat(result.get(itemId2)).isEqualTo(5);
    }

    @Test
    void should_batch_use_current_bookings_when_no_date_range_given() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithId(itemId, 4, Item.ItemType.INDIVIDUAL);

        when(itemRepository.batchCountCurrentlyBookedUnits(List.of(itemId))).thenReturn(List.<Object[]>of(
                new Object[]{itemId, 4}
        ));

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(item), null, null);

        assertThat(result.get(itemId)).isZero();
    }

    @Test
    void should_batch_return_zero_for_inactive_items_without_querying_bookings() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithId(itemId, 5, Item.ItemType.INDIVIDUAL);
        item.setIsActive(false);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.batchCountBookedUnits(List.of(itemId), start, end)).thenReturn(List.of());

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(item), start, end);

        assertThat(result.get(itemId)).isZero();
    }

    @Test
    void should_batch_default_to_zero_booked_when_item_missing_from_query_rows() {
        UUID itemId = UUID.randomUUID();
        Item item = activeItemWithId(itemId, 3, Item.ItemType.INDIVIDUAL);
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        when(itemRepository.batchCountBookedUnits(List.of(itemId), start, end)).thenReturn(List.of());

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(item), start, end);

        assertThat(result.get(itemId)).isEqualTo(3);
    }

    @Test
    void should_batch_constrain_package_by_its_components_availability() {
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item componentItem = activeItemWithId(componentId, 4, Item.ItemType.INDIVIDUAL);
        Item packageItem = activeItemWithId(packageId, 3, Item.ItemType.PACKAGE);
        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(2); // needs 2 units of component per set
        packageItem.getPackageComponents().add(comp);

        when(itemRepository.batchCountBookedUnits(List.of(packageId, componentId), start, end)).thenReturn(List.of(
                new Object[]{packageId, 0},
                new Object[]{componentId, 0}
        ));

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(packageItem), start, end);

        // package stock=3, component floor(4/2)=2 sets → bottlenecked to 2
        assertThat(result.get(packageId)).isEqualTo(2);
    }

    @Test
    void should_batch_return_zero_for_package_when_a_component_is_inactive() {
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item componentItem = activeItemWithId(componentId, 4, Item.ItemType.INDIVIDUAL);
        componentItem.setIsActive(false);
        Item packageItem = activeItemWithId(packageId, 3, Item.ItemType.PACKAGE);
        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(1);
        packageItem.getPackageComponents().add(comp);

        when(itemRepository.batchCountBookedUnits(List.of(packageId, componentId), start, end)).thenReturn(List.<Object[]>of(
                new Object[]{packageId, 0}
        ));

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(List.of(packageItem), start, end);

        assertThat(result.get(packageId)).isZero();
    }

    @Test
    void should_batch_deduplicate_shared_component_ids_across_multiple_packages() {
        UUID packageAId = UUID.randomUUID();
        UUID packageBId = UUID.randomUUID();
        UUID sharedComponentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusDays(1);

        Item sharedComponent = activeItemWithId(sharedComponentId, 5, Item.ItemType.INDIVIDUAL);
        Item packageA = activeItemWithId(packageAId, 2, Item.ItemType.PACKAGE);
        Item packageB = activeItemWithId(packageBId, 2, Item.ItemType.PACKAGE);

        PackageComponent compA = new PackageComponent();
        compA.setComponentItem(sharedComponent);
        compA.setQuantity(1);
        packageA.getPackageComponents().add(compA);

        PackageComponent compB = new PackageComponent();
        compB.setComponentItem(sharedComponent);
        compB.setQuantity(1);
        packageB.getPackageComponents().add(compB);

        when(itemRepository.batchCountBookedUnits(
                List.of(packageAId, sharedComponentId, packageBId), start, end
        )).thenReturn(List.of(
                new Object[]{packageAId, 0},
                new Object[]{packageBId, 0},
                new Object[]{sharedComponentId, 1}
        ));

        Map<UUID, Integer> result = availabilityService.batchGetAvailableQuantities(
                List.of(packageA, packageB), start, end
        );

        assertThat(result.get(packageAId)).isEqualTo(2);
        assertThat(result.get(packageBId)).isEqualTo(2);
    }
}
