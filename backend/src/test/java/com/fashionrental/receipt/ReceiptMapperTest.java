package com.fashionrental.receipt;

import com.fashionrental.customer.Customer;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemPhoto;
import com.fashionrental.receipt.model.response.ReceiptLineItemResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptMapperTest {

    private final ReceiptMapper mapper = new ReceiptMapper();

    @Test
    void toLineItemResponse_uses_first_photo_in_the_ordered_collection() {
        // Item.photos is @OrderBy("sortOrder ASC"): Hibernate loads it pre-sorted, so the
        // sortOrder-0 photo is always at index 0 by the time the mapper sees the list.
        Item item = TestData.item("Sherwani", 300, 1000);
        item.getPhotos().add(photo(item, 0, "https://r2.example/thumb-0.jpg"));
        item.getPhotos().add(photo(item, 1, "https://r2.example/thumb-1.jpg"));

        ReceiptResponse response = mapReceiptWith(item);

        assertThat(response.lineItems()).hasSize(1);
        assertThat(response.lineItems().get(0).thumbnailUrl()).isEqualTo("https://r2.example/thumb-0.jpg");
    }

    @Test
    void toLineItemResponse_returns_null_thumbnail_when_item_has_no_photos() {
        Item item = TestData.item("Sherwani", 300, 1000);

        ReceiptResponse response = mapReceiptWith(item);

        assertThat(response.lineItems()).hasSize(1);
        assertThat(response.lineItems().get(0).thumbnailUrl()).isNull();
    }

    @Test
    void toReceiptResponse_gives_the_package_line_and_its_component_line_each_their_own_photo() {
        // A package receipt bills the PACKAGE item as one line, then reserves each of its
        // components as a separate zero-rate/zero-deposit line (see CLAUDE.md "Item packages").
        // Each line's item is distinct, so the mapper must not conflate the two thumbnails.
        Item packageItem = TestData.item("Wedding Package", 500, 2000);
        packageItem.setItemType(Item.ItemType.PACKAGE);
        packageItem.getPhotos().add(photo(packageItem, 0, "https://r2.example/package-thumb.jpg"));

        Item componentItem = TestData.item("Turban", 100, 300);
        componentItem.getPhotos().add(photo(componentItem, 0, "https://r2.example/component-thumb.jpg"));

        ReceiptLineItem packageLine = TestData.receiptLineItem(packageItem, 1);
        ReceiptLineItem componentLine = TestData.receiptLineItem(componentItem, 1);
        componentLine.setRateSnapshot(0);
        componentLine.setDepositSnapshot(0);
        componentLine.setLineRent(0);
        componentLine.setLineDeposit(0);

        Customer customer = TestData.customer("Asha", "9812345678");
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-11T10:00:00+05:30");
        Receipt receipt = TestData.receipt(customer, start, end, packageLine, componentLine);

        ReceiptResponse response = mapper.toReceiptResponse(receipt);

        assertThat(response.lineItems()).hasSize(2);
        ReceiptLineItemResponse packageResponse = response.lineItems().get(0);
        ReceiptLineItemResponse componentResponse = response.lineItems().get(1);

        assertThat(packageResponse.itemId()).isEqualTo(packageItem.getId());
        assertThat(packageResponse.thumbnailUrl()).isEqualTo("https://r2.example/package-thumb.jpg");

        assertThat(componentResponse.itemId()).isEqualTo(componentItem.getId());
        assertThat(componentResponse.thumbnailUrl()).isEqualTo("https://r2.example/component-thumb.jpg");
    }

    private ReceiptResponse mapReceiptWith(Item item) {
        Customer customer = TestData.customer("Asha", "9812345678");
        ReceiptLineItem lineItem = TestData.receiptLineItem(item, 1);
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-11T10:00:00+05:30");
        Receipt receipt = TestData.receipt(customer, start, end, lineItem);

        return mapper.toReceiptResponse(receipt);
    }

    private ItemPhoto photo(Item item, int sortOrder, String thumbnailUrl) {
        ItemPhoto photo = new ItemPhoto();
        photo.setItem(item);
        photo.setUrl(thumbnailUrl);
        photo.setThumbnailUrl(thumbnailUrl);
        photo.setSortOrder(sortOrder);
        return photo;
    }
}
