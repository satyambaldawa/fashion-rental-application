package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.inventory.model.request.CreateItemRequest;
import com.fashionrental.inventory.model.response.ItemDetailResponse;
import com.fashionrental.inventory.model.response.ItemPhotoResponse;
import com.fashionrental.inventory.model.response.ItemSummaryResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final AvailabilityService availabilityService;

    public ItemService(ItemRepository itemRepository, AvailabilityService availabilityService) {
        this.itemRepository = itemRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional(readOnly = true)
    public Page<ItemSummaryResponse> listItems(String search, Item.Category category, String itemSize, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        Specification<Item> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("isActive")));
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (itemSize != null && !itemSize.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("size")), "%" + itemSize.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return itemRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public ItemDetailResponse getItem(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));

        if (!item.getIsActive()) {
            throw new ResourceNotFoundException("Item not found: " + id);
        }

        return toDetailResponse(item);
    }

    @Transactional
    public ItemDetailResponse createItem(CreateItemRequest request) {
        Item item = new Item();
        item.setName(request.name());
        item.setCategory(request.category());
        item.setSize(request.size());
        item.setDescription(request.description());
        item.setRate(request.rate());
        item.setDeposit(request.deposit());
        item.setQuantity(request.quantity());
        item.setNotes(request.notes());

        Item saved = itemRepository.save(item);
        return toDetailResponse(saved);
    }

    private ItemSummaryResponse toSummaryResponse(Item item) {
        int available = availabilityService.getAvailableQuantity(item.getId(), null, null);
        List<String> photoUrls = item.getPhotos().stream().map(ItemPhoto::getUrl).toList();
        String thumbnailUrl = item.getPhotos().isEmpty() ? null : item.getPhotos().get(0).getThumbnailUrl();

        return new ItemSummaryResponse(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getSize(),
                item.getRate(),
                item.getDeposit(),
                item.getQuantity(),
                available,
                available > 0,
                thumbnailUrl,
                photoUrls
        );
    }

    private ItemDetailResponse toDetailResponse(Item item) {
        List<ItemPhotoResponse> photos = item.getPhotos().stream()
                .map(this::toPhotoResponse)
                .toList();

        return new ItemDetailResponse(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getItemType(),
                item.getSize(),
                item.getDescription(),
                item.getRate(),
                item.getDeposit(),
                item.getQuantity(),
                item.getIsActive(),
                item.getNotes(),
                photos,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private ItemPhotoResponse toPhotoResponse(ItemPhoto photo) {
        return new ItemPhotoResponse(
                photo.getId(),
                photo.getUrl(),
                photo.getThumbnailUrl(),
                photo.getSortOrder()
        );
    }
}
