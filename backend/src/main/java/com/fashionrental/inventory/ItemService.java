package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.model.request.CreateItemRequest;
import com.fashionrental.inventory.model.request.PackageComponentRequest;
import com.fashionrental.inventory.model.request.UpdateItemRequest;
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

import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final AvailabilityService availabilityService;
    private final PackageComponentRepository packageComponentRepository;
    private final ImageStorageService imageStorageService;

    public ItemService(ItemRepository itemRepository,
                       AvailabilityService availabilityService,
                       PackageComponentRepository packageComponentRepository,
                       ImageStorageService imageStorageService) {
        this.itemRepository = itemRepository;
        this.availabilityService = availabilityService;
        this.packageComponentRepository = packageComponentRepository;
        this.imageStorageService = imageStorageService;
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

    @Transactional
    public ItemDetailResponse cloneItem(UUID sourceItemId) {
        Item source = itemRepository.findById(sourceItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sourceItemId));

        if (!source.getIsActive()) {
            throw new ResourceNotFoundException("Item not found: " + sourceItemId);
        }

        Item clone = new Item();
        clone.setName(source.getName() + " (copy)");
        clone.setCategory(source.getCategory());
        clone.setItemType(source.getItemType());
        clone.setSize(source.getSize());
        clone.setDescription(source.getDescription());
        clone.setRate(source.getRate());
        clone.setDeposit(source.getDeposit());
        clone.setQuantity(source.getQuantity());
        clone.setNotes(source.getNotes());
        clone.setPurchaseRate(source.getPurchaseRate());
        clone.setVendorName(source.getVendorName());

        // Copy package components
        if (source.getItemType() == Item.ItemType.PACKAGE) {
            for (PackageComponent srcPc : source.getPackageComponents()) {
                PackageComponent pc = new PackageComponent();
                pc.setPackageItem(clone);
                pc.setComponentItem(srcPc.getComponentItem());
                pc.setQuantity(srcPc.getQuantity());
                clone.getPackageComponents().add(pc);
            }
        }

        Item saved = itemRepository.save(clone);

        // Copy photos
        for (ItemPhoto srcPhoto : source.getPhotos()) {
            try {
                UploadResult result = imageStorageService.copyImage(
                        saved.getId(), srcPhoto.getUrl(), srcPhoto.getThumbnailUrl());

                ItemPhoto newPhoto = new ItemPhoto();
                newPhoto.setItem(saved);
                newPhoto.setUrl(result.fullUrl());
                newPhoto.setThumbnailUrl(result.thumbnailUrl());
                newPhoto.setSortOrder(srcPhoto.getSortOrder());
                saved.getPhotos().add(newPhoto);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy photo: " + e.getMessage(), e);
            }
        }

        if (!source.getPhotos().isEmpty()) {
            saved = itemRepository.save(saved);
        }

        return toDetailResponse(saved);
    }

    @Transactional
    public void deleteItem(UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));

        if (!item.getIsActive()) {
            throw new ResourceNotFoundException("Item not found: " + id);
        }

        // Guard: block deletion if this item is a component of any active package
        List<PackageComponent> parentLinks =
                packageComponentRepository.findByComponentItem_IdAndPackageItem_IsActiveTrue(id);
        if (!parentLinks.isEmpty()) {
            List<String> packageNames = parentLinks.stream()
                    .map(pc -> pc.getPackageItem().getName())
                    .distinct()
                    .toList();
            throw new ValidationException(
                    "Cannot delete '" + item.getName() + "': it is a component of active package(s): " +
                    String.join(", ", packageNames) +
                    ". Remove it from these packages first.");
        }

        item.setIsActive(false);
        itemRepository.save(item);
    }

    @Transactional
    public ItemDetailResponse updateItem(UUID id, UpdateItemRequest request) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));

        if (!item.getIsActive()) {
            throw new ResourceNotFoundException("Item not found: " + id);
        }

        // Guard: prevent reducing quantity below currently rented-out units
        if (request.quantity() < item.getQuantity()) {
            int available = availabilityService.getAvailableQuantity(id, null, null);
            int currentlyBooked = item.getQuantity() - available;
            if (request.quantity() < currentlyBooked) {
                throw new ValidationException(
                        "Cannot reduce quantity to " + request.quantity() +
                        ": " + currentlyBooked + " unit(s) are currently rented out.");
            }
        }

        item.setName(request.name());
        item.setCategory(request.category());
        item.setSize(request.size());
        item.setDescription(request.description());
        item.setRate(request.rate());
        item.setDeposit(request.deposit());
        item.setQuantity(request.quantity());
        item.setNotes(request.notes());
        item.setPurchaseRate(request.purchaseRate());
        item.setVendorName(request.vendorName());

        // Update package components if this is a PACKAGE item
        if (item.getItemType() == Item.ItemType.PACKAGE) {
            item.getPackageComponents().clear();
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

            if (!componentItem.getIsActive()) {
                throw new ValidationException(
                        "Component item '" + componentItem.getName() + "' has been deleted.");
            }

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
