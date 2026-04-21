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

    @InjectMocks
    private AvailabilityService availabilityService;

    private Item activeItemWithQuantity(UUID id, int quantity) {
        Item item = new Item();
        item.setQuantity(quantity);
        item.setIsActive(true);
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
}
