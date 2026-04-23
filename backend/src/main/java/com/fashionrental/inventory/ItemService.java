package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.model.request.CreateItemRequest;
import com.fashionrental.inventory.model.request.PackageComponentRequest;
import com.fashionrental.inventory.model.response.ItemDetailResponse;
import com.fashionrental.inventory.model.response.ItemPhotoResponse;
import com.fashionrental.inventory.model.response.ItemSummaryResponse;
import com.fashionrental.inventory.model.response.PackageComponentResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final AvailabilityService availabilityService;
    private final PackageComponentRepository packageComponentRepository;

    public ItemService(ItemRepository itemRepository,
                       AvailabilityService availabilityService,
                       PackageComponentRepository packageComponentRepository) {
        this.itemRepository = itemRepository;
        this.availabilityService = availabilityService;
        this.packageComponentRepository = packageComponentRepository;
    }

    @Transactional(readOnly = true)
    public Page<ItemSummaryResponse> listItems(String search, Item.Category category, String itemSize,
                                               int page, int size,
                                               OffsetDateTime startDatetime, OffsetDateTime endDatetime) {
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

        return itemRepository.findAll(spec, pageable)
                .map(item -> toSummaryResponse(item, startDatetime, endDatetime));
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
        Item.ItemType itemType = request.itemType() != null ? request.itemType() : Item.ItemType.INDIVIDUAL;

        Item item = new Item();
        item.setName(request.name());
        item.setCategory(request.category());
        item.setItemType(itemType);
        item.setSize(request.size());
        item.setDescription(request.description());
        item.setRate(request.rate());
        item.setDeposit(request.deposit());
        item.setQuantity(request.quantity());
        item.setNotes(request.notes());
        item.setPurchaseRate(request.purchaseRate());
        item.setVendorName(request.vendorName());

        if (itemType == Item.ItemType.PACKAGE) {
            validateAndAttachComponents(item, request.components());
        }

        Item saved = itemRepository.save(item);
        return toDetailResponse(saved);
    }

    private void validateAndAttachComponents(Item packageItem, List<PackageComponentRequest> componentRequests) {
        if (componentRequests == null || componentRequests.isEmpty()) {
            throw new ValidationException("A package must have at least one component item.");
        }

        for (PackageComponentRequest req : componentRequests) {
            Item componentItem = itemRepository.findById(req.componentItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Component item not found: " + req.componentItemId()));

            if (componentItem.getItemType() != Item.ItemType.INDIVIDUAL) {
                throw new ValidationException(
                        "Only INDIVIDUAL items can be package components. '" +
                        componentItem.getName() + "' is a package.");
            }

            PackageComponent pc = new PackageComponent();
            pc.setPackageItem(packageItem);
            pc.setComponentItem(componentItem);
            pc.setQuantity(req.quantity());
            packageItem.getPackageComponents().add(pc);
        }
    }

    private ItemSummaryResponse toSummaryResponse(Item item, OffsetDateTime startDatetime, OffsetDateTime endDatetime) {
        int available = availabilityService.getAvailableQuantity(item.getId(), startDatetime, endDatetime);
        List<String> photoUrls = item.getPhotos().stream().map(ItemPhoto::getUrl).toList();
        String thumbnailUrl = item.getPhotos().isEmpty() ? null : item.getPhotos().get(0).getThumbnailUrl();

        List<String> componentNames = null;
        if (item.getItemType() == Item.ItemType.PACKAGE) {
            componentNames = item.getPackageComponents().stream()
                    .map(pc -> pc.getComponentItem().getName() + " ×" + pc.getQuantity())
                    .toList();
        }

        return new ItemSummaryResponse(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getItemType(),
                item.getSize(),
                item.getDescription(),
                item.getRate(),
                item.getDeposit(),
                item.getQuantity(),
                available,
                available > 0,
                thumbnailUrl,
                photoUrls,
                componentNames
        );
    }

    private ItemDetailResponse toDetailResponse(Item item) {
        List<ItemPhotoResponse> photos = item.getPhotos().stream()
                .map(this::toPhotoResponse)
                .toList();

        List<PackageComponentResponse> components = null;
        if (item.getItemType() == Item.ItemType.PACKAGE) {
            components = item.getPackageComponents().stream()
                    .map(pc -> {
                        Item comp = pc.getComponentItem();
                        List<ItemPhotoResponse> compPhotos = comp.getPhotos().stream()
                                .map(this::toPhotoResponse)
                                .toList();
                        return new PackageComponentResponse(
                                comp.getId(),
                                comp.getName(),
                                comp.getCategory(),
                                comp.getSize(),
                                comp.getDescription(),
                                compPhotos,
                                pc.getQuantity()
                        );
                    })
                    .toList();
        }

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
                item.getPurchaseRate(),
                item.getVendorName(),
                components,
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
