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
}
