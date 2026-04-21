package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final ItemRepository itemRepository;

    public AvailabilityService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public int getAvailableQuantity(UUID itemId, OffsetDateTime start, OffsetDateTime end) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        if (!item.getIsActive()) {
            return 0;
        }

        int booked;
        if (start == null || end == null) {
            booked = itemRepository.countCurrentlyBookedUnits(itemId);
        } else {
            booked = itemRepository.countBookedUnits(itemId, start, end);
        }

        return Math.max(0, item.getQuantity() - booked);
    }

    public Map<UUID, Integer> getAvailableQuantities(List<UUID> itemIds, OffsetDateTime start, OffsetDateTime end) {
        return itemIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> getAvailableQuantity(id, start, end)
                ));
    }
}
