package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.AvailabilityService;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponent;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutPreviewRequest;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock ItemRepository itemRepository;
    @Mock CustomerRepository customerRepository;
    @Mock AvailabilityService availabilityService;
    @Mock PackageComponentRepository packageComponentRepository;
    @Mock ReceiptRepository receiptRepository;
    @Mock ReceiptNumberService receiptNumberService;
    @Mock DateTimeUtil dateTimeUtil;
    @Mock ReceiptMapper receiptMapper;

    @InjectMocks CheckoutService checkoutService;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

    // ─── Preview ─────────────────────────────────────────────────────────────

    @Test
    void should_calculate_preview_totals_correctly() {
        UUID itemId = UUID.randomUUID();
        Item item = makeItem(itemId, "Blue Sherwani", 200, 1000);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(itemId, START, END)).thenReturn(5);
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);

        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END,
                List.of(new CheckoutLineItem(itemId, 2))
        );

        CheckoutPreviewResponse preview = checkoutService.preview(request);

        assertThat(preview.rentalDays()).isEqualTo(3);
        assertThat(preview.lineItems()).hasSize(1);
        assertThat(preview.lineItems().get(0).lineRent()).isEqualTo(1200);   // 200 * 3 * 2
        assertThat(preview.lineItems().get(0).lineDeposit()).isEqualTo(2000); // 1000 * 2
        assertThat(preview.totalRent()).isEqualTo(1200);
        assertThat(preview.totalDeposit()).isEqualTo(2000);
        assertThat(preview.grandTotal()).isEqualTo(3200);
        assertThat(preview.allAvailable()).isTrue();
        assertThat(preview.unavailableItems()).isEmpty();
    }

    @Test
    void should_flag_unavailable_items_in_preview() {
        UUID itemId = UUID.randomUUID();
        Item item = makeItem(itemId, "Gold Necklace", 100, 500);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(itemId, START, END)).thenReturn(1);
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(2);

        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END,
                List.of(new CheckoutLineItem(itemId, 2)) // requesting 2, only 1 available
        );

        CheckoutPreviewResponse preview = checkoutService.preview(request);

        assertThat(preview.allAvailable()).isFalse();
        assertThat(preview.unavailableItems()).containsExactly("Gold Necklace");
    }

    @Test
    void should_throw_validation_when_end_before_start() {
        OffsetDateTime end = START.minusDays(1);

        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, end,
                List.of(new CheckoutLineItem(UUID.randomUUID(), 1))
        );

        assertThatThrownBy(() -> checkoutService.preview(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("endDatetime must be after startDatetime");
    }

    // ─── Create Receipt ───────────────────────────────────────────────────────

    @Test
    void should_create_receipt_with_snapshots() {
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        Customer customer = new Customer();
        customer.setName("Ramesh");
        customer.setPhone("9876543210");
        customer.setCustomerType(Customer.CustomerType.MISC);

        Item item = new Item();
        item.setName("Blue Sherwani");
        item.setCategory(Item.Category.COSTUME);
        item.setRate(300);
        item.setDeposit(1500);
        item.setQuantity(3);
        item.setIsActive(true);

        CheckoutRequest request = new CheckoutRequest(
                customerId,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(2),
                List.of(new CheckoutLineItem(itemId, 1)),
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(eq(itemId), any(), any())).thenReturn(3);
        when(dateTimeUtil.calculateRentalDays(any(), any())).thenReturn(2);
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-20260422-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());
        Receipt saved = captor.getValue();

        assertThat(saved.getLineItems()).hasSize(1);
        ReceiptLineItem li = saved.getLineItems().get(0);
        assertThat(li.getRateSnapshot()).isEqualTo(300);
        assertThat(li.getDepositSnapshot()).isEqualTo(1500);
        assertThat(li.getLineRent()).isEqualTo(300 * 2 * 1);   // rate * days * qty
        assertThat(li.getLineDeposit()).isEqualTo(1500 * 1);    // deposit * qty
    }

    @Test
    void should_throw_conflict_when_item_unavailable_at_creation() {
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        Customer customer = makeCustomer(customerId, "Test User", "9000000001");
        Item item = makeItem(itemId, "Blue Pagdi", 150, 500);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(itemId, START, END)).thenReturn(0);
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(1);

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(itemId, 1)),
                null
        );

        assertThatThrownBy(() -> checkoutService.createReceipt(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Blue Pagdi");
    }

    @Test
    void should_throw_not_found_when_customer_missing() {
        UUID customerId = UUID.randomUUID();

        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(1);

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(UUID.randomUUID(), 1)),
                null
        );

        assertThatThrownBy(() -> checkoutService.createReceipt(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void should_enforce_minimum_1_rental_day() {
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        Customer customer = makeCustomer(customerId, "Test User", "9000000002");
        Item item = makeItem(itemId, "Silver Ring", 50, 200);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(itemId, START, END)).thenReturn(5);
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(1); // minimum enforced by DateTimeUtil
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-20260421-001");

        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptMapper.toReceiptResponse(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            return new ReceiptResponse(null, null, null, null, null, null, null,
                    r.getRentalDays(), 0, 0, 0, null, null, List.of(), null);
        });

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(itemId, 1)),
                null
        );

        ReceiptResponse response = checkoutService.createReceipt(request);

        assertThat(response.rentalDays()).isGreaterThanOrEqualTo(1);
    }

    // ─── Package component reservation ───────────────────────────────────────

    @Test
    void should_add_zero_rate_reservation_lines_for_package_components_on_checkout() {
        UUID customerId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();

        Customer customer = makeCustomer(customerId, "Ramesh", "9876543210");

        Item componentItem = new Item();
        componentItem.setName("Pagdi");
        componentItem.setCategory(Item.Category.PAGDI);
        componentItem.setRate(100);
        componentItem.setDeposit(500);
        componentItem.setQuantity(5);
        componentItem.setIsActive(true);
        injectId(componentItem, componentId);

        Item packageItem = new Item();
        packageItem.setName("Maharaja Set");
        packageItem.setCategory(Item.Category.COSTUME);
        packageItem.setItemType(Item.ItemType.PACKAGE);
        packageItem.setRate(500);
        packageItem.setDeposit(2000);
        packageItem.setQuantity(3);
        packageItem.setIsActive(true);
        injectId(packageItem, packageId);

        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(1);

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(packageId, 1)),
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(packageId)).thenReturn(Optional.of(packageItem));
        when(availabilityService.getAvailableQuantity(packageId, START, END)).thenReturn(3);
        when(packageComponentRepository.findByPackageItem_Id(packageId)).thenReturn(List.of(comp));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-20260422-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());
        Receipt saved = captor.getValue();

        // 1 billed line for the package + 1 zero-rate reservation for the component
        assertThat(saved.getLineItems()).hasSize(2);

        ReceiptLineItem billedLine = saved.getLineItems().get(0);
        assertThat(billedLine.getRateSnapshot()).isEqualTo(500);
        assertThat(billedLine.getDepositSnapshot()).isEqualTo(2000);
        assertThat(billedLine.getLineRent()).isEqualTo(500 * 3 * 1);

        ReceiptLineItem reservationLine = saved.getLineItems().get(1);
        assertThat(reservationLine.getItem().getName()).isEqualTo("Pagdi");
        assertThat(reservationLine.getRateSnapshot()).isZero();
        assertThat(reservationLine.getDepositSnapshot()).isZero();
        assertThat(reservationLine.getLineRent()).isZero();
        assertThat(reservationLine.getLineDeposit()).isZero();
        assertThat(reservationLine.getQuantity()).isEqualTo(1); // 1 per set × 1 package
    }

    @Test
    void should_scale_component_reservation_quantity_by_package_quantity() {
        // Renting 2 packages, component ×2 per set → reserve 4 component units
        UUID customerId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();

        Customer customer = makeCustomer(customerId, "Suresh", "9000000099");

        Item componentItem = new Item();
        componentItem.setName("Belt");
        componentItem.setCategory(Item.Category.ACCESSORIES);
        componentItem.setRate(50);
        componentItem.setDeposit(200);
        componentItem.setQuantity(10);
        componentItem.setIsActive(true);
        injectId(componentItem, componentId);

        Item packageItem = new Item();
        packageItem.setName("Warrior Set");
        packageItem.setCategory(Item.Category.COSTUME);
        packageItem.setItemType(Item.ItemType.PACKAGE);
        packageItem.setRate(400);
        packageItem.setDeposit(1500);
        packageItem.setQuantity(5);
        packageItem.setIsActive(true);
        injectId(packageItem, packageId);

        PackageComponent comp = new PackageComponent();
        comp.setComponentItem(componentItem);
        comp.setQuantity(2); // 2 belts per set

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(packageId, 2)), // renting 2 packages
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(packageId)).thenReturn(Optional.of(packageItem));
        when(availabilityService.getAvailableQuantity(packageId, START, END)).thenReturn(5);
        when(packageComponentRepository.findByPackageItem_Id(packageId)).thenReturn(List.of(comp));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-20260422-002");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());
        Receipt saved = captor.getValue();

        ReceiptLineItem reservationLine = saved.getLineItems().get(1);
        assertThat(reservationLine.getQuantity()).isEqualTo(4); // 2 per set × 2 packages
    }

    private void injectId(Item item, UUID id) {
        try {
            var field = Item.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(item, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Item makeItem(UUID id, String name, int rate, int deposit) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setRate(rate);
        item.setDeposit(deposit);
        item.setQuantity(10);
        item.setIsActive(true);
        // inject id via reflection since there's no setter
        try {
            var field = Item.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(item, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return item;
    }

    private Customer makeCustomer(UUID id, String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        try {
            var field = Customer.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(customer, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return customer;
    }

    private Receipt buildReceipt(UUID customerId, Customer customer, UUID itemId, Item item, int rentalDays) {
        ReceiptLineItem li = new ReceiptLineItem();
        li.setItem(item);
        li.setQuantity(1);
        li.setRateSnapshot(item.getRate());
        li.setDepositSnapshot(item.getDeposit());
        li.setLineRent(item.getRate() * rentalDays);
        li.setLineDeposit(item.getDeposit());

        Receipt receipt = new Receipt();
        receipt.setCustomer(customer);
        receipt.setStartDatetime(START);
        receipt.setEndDatetime(END);
        receipt.setRentalDays(rentalDays);
        receipt.setReceiptNumber("R-20260421-001");
        receipt.setStatus(Receipt.Status.GIVEN);
        receipt.setTotalRent(li.getLineRent());
        receipt.setTotalDeposit(li.getLineDeposit());
        receipt.setGrandTotal(li.getLineRent() + li.getLineDeposit());
        receipt.getLineItems().add(li);
        li.setReceipt(receipt);

        try {
            var idField = Receipt.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(receipt, UUID.randomUUID());

            var createdAtField = Receipt.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(receipt, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return receipt;
    }
}
