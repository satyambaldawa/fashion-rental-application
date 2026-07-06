package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final ItemRepository itemRepository;
    private final PackageComponentRepository packageComponentRepository;

    public AvailabilityService(ItemRepository itemRepository,
                               PackageComponentRepository packageComponentRepository) {
        this.itemRepository = itemRepository;
        this.packageComponentRepository = packageComponentRepository;
    }

    public int getAvailableQuantity(UUID itemId, OffsetDateTime start, OffsetDateTime end) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        if (!item.getIsActive()) {
            return 0;
        }

        int booked = (start == null || end == null)
                ? itemRepository.countCurrentlyBookedUnits(itemId)
                : itemRepository.countBookedUnits(itemId, start, end);

        int available = Math.max(0, item.getQuantity() - booked);

        // For packages: further constrain by each component's availability
        if (item.getItemType() == Item.ItemType.PACKAGE) {
            List<PackageComponent> components = packageComponentRepository.findByPackageItem_Id(itemId);
            for (PackageComponent comp : components) {
                int compAvail = getAvailableQuantity(comp.getComponentItem().getId(), start, end);
                // How many complete sets can this component cover?
                int setsFromComp = compAvail / comp.getQuantity();
                available = Math.min(available, setsFromComp);
            }
        }

        return available;
    }

    public Map<UUID, Integer> getAvailableQuantities(List<UUID> itemIds, OffsetDateTime start, OffsetDateTime end) {
        return itemIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> getAvailableQuantity(id, start, end)
                ));
    }

    /**
     * Batch availability check for a list of already-loaded items.
     * Uses a single SQL query instead of N individual queries.
     */
    public Map<UUID, Integer> batchGetAvailableQuantities(List<Item> items, OffsetDateTime start, OffsetDateTime end) {
        if (items.isEmpty()) {
            return Map.of();
        }

        // Collect all item IDs including package component IDs
        List<UUID> allItemIds = new ArrayList<>();
        for (Item item : items) {
            allItemIds.add(item.getId());
            if (item.getItemType() == Item.ItemType.PACKAGE) {
                for (PackageComponent pc : item.getPackageComponents()) {
                    allItemIds.add(pc.getComponentItem().getId());
                }
            }
        }
        List<UUID> distinctIds = allItemIds.stream().distinct().toList();

        // Single batch query for all booked counts
        List<Object[]> rows = (start == null || end == null)
                ? itemRepository.batchCountCurrentlyBookedUnits(distinctIds)
                : itemRepository.batchCountBookedUnits(distinctIds, start, end);

        Map<UUID, Integer> bookedMap = new java.util.HashMap<>();
        for (Object[] row : rows) {
            UUID itemId = (UUID) row[0];
            int booked = ((Number) row[1]).intValue();
            bookedMap.put(itemId, booked);
        }

        // Build result map — mirror the isActive guards from getAvailableQuantity()
        Map<UUID, Integer> result = new java.util.HashMap<>();
        for (Item item : items) {
            if (!item.getIsActive()) {
                result.put(item.getId(), 0);
                continue;
            }

            int booked = bookedMap.getOrDefault(item.getId(), 0);
            int available = Math.max(0, item.getQuantity() - booked);

            if (item.getItemType() == Item.ItemType.PACKAGE) {
                for (PackageComponent comp : item.getPackageComponents()) {
                    Item compItem = comp.getComponentItem();
                    if (!compItem.getIsActive()) {
                        available = 0;
                        break;
                    }
                    int compBooked = bookedMap.getOrDefault(compItem.getId(), 0);
                    int compAvail = Math.max(0, compItem.getQuantity() - compBooked);
                    int setsFromComp = compAvail / comp.getQuantity();
                    available = Math.min(available, setsFromComp);
                }
            }

            result.put(item.getId(), available);
        }

        return result;
    }
}
