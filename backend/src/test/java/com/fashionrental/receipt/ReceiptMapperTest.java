package com.fashionrental.receipt;

import com.fashionrental.config.AppUser;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.customer.Customer;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemPhoto;
import com.fashionrental.receipt.model.response.EligibleCouponResponse;
import com.fashionrental.receipt.model.response.ReceiptLineItemResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptMapperTest {

    private final ReceiptMapper mapper = new ReceiptMapper();

    @Test
    void toEligibleCouponResponse_maps_the_coupon_terms_and_the_discount_computed_for_this_cart() {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE20");
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(20);
        coupon.setMinSubtotal(200);
        coupon.setValidTo(OffsetDateTime.parse("2026-06-30T23:59:59+05:30"));

        AppliedDiscount discount = new AppliedDiscount(coupon, "SAVE20", 60);

        EligibleCouponResponse response = mapper.toEligibleCouponResponse(discount);

        assertThat(response.code()).isEqualTo("SAVE20");
        assertThat(response.discountType()).isEqualTo("PERCENT");
        assertThat(response.value()).isEqualTo(20);
        assertThat(response.minSubtotal()).isEqualTo(200);
        assertThat(response.validTo()).isEqualTo(coupon.getValidTo());
        assertThat(response.discountAmount()).isEqualTo(60);
    }

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

    @Test
    void toReceiptResponse_maps_the_cancellation_block_for_a_cancelled_receipt() {
        Item item = TestData.item("Sherwani", 300, 1000);
        Customer customer = TestData.customer("Asha", "9812345678");
        ReceiptLineItem lineItem = TestData.receiptLineItem(item, 1);
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-11T10:00:00+05:30");
        Receipt receipt = TestData.receipt(customer, start, end, lineItem);

        AppUser owner = new AppUser();
        owner.setUsername("owner");
        OffsetDateTime cancelledAt = OffsetDateTime.parse("2026-04-10T12:00:00+05:30");
        receipt.cancel(owner, cancelledAt, Receipt.CancellationReason.OTHER, "customer changed plans");

        ReceiptResponse response = mapper.toReceiptResponse(receipt);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancellation()).isNotNull();
        assertThat(response.cancellation().cancelledAt()).isEqualTo(cancelledAt);
        assertThat(response.cancellation().cancelledByUsername()).isEqualTo("owner");
        assertThat(response.cancellation().reason()).isEqualTo("OTHER");
        assertThat(response.cancellation().reasonDetail()).isEqualTo("customer changed plans");
    }

    @Test
    void toReceiptResponse_leaves_cancellation_null_for_a_given_receipt() {
        ReceiptResponse response = mapReceiptWith(TestData.item("Sherwani", 300, 1000));

        assertThat(response.cancellation()).isNull();
    }

    @Test
    void toPublicReceiptResponse_keeps_status_cancelled_but_nulls_the_cancellation_block() {
        Item item = TestData.item("Sherwani", 300, 1000);
        Customer customer = TestData.customer("Asha", "9812345678");
        ReceiptLineItem lineItem = TestData.receiptLineItem(item, 1);
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-11T10:00:00+05:30");
        Receipt receipt = TestData.receipt(customer, start, end, lineItem);

        AppUser owner = new AppUser();
        owner.setUsername("owner");
        receipt.cancel(owner, OffsetDateTime.now(), Receipt.CancellationReason.WRONG_ORDER, null);

        ReceiptResponse response = mapper.toPublicReceiptResponse(receipt);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancellation()).isNull();
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
