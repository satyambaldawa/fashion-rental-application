package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.common.util.ShareTokenService;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.AvailabilityService;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponent;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.receipt.model.request.AdHocLineItem;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutPreviewRequest;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.PreviewLineItem;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock ShareTokenService shareTokenService;
    @Mock DateTimeUtil dateTimeUtil;
    @Mock ReceiptMapper receiptMapper;

    @InjectMocks CheckoutService checkoutService;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

    @BeforeEach
    void authenticateAsOwner() {
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
                List.of(new CheckoutLineItem(itemId, 2)),
                List.of()
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
                List.of(new CheckoutLineItem(itemId, 2)), // requesting 2, only 1 available
                List.of()
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
                List.of(new CheckoutLineItem(UUID.randomUUID(), 1)),
                List.of()
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
                List.of(),
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
                List.of(),
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
                List.of(),
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
            return new ReceiptResponse(null, null, null, null, null, null, null, null,
                    r.getRentalDays(), 0, 0, 0, null, null, List.of(), null);
        });

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(itemId, 1)),
                List.of(),
                null
        );

        ReceiptResponse response = checkoutService.createReceipt(request);

        assertThat(response.rentalDays()).isGreaterThanOrEqualTo(1);
    }

    // ─── Ad-hoc line items ──────────────────────────────────────────────────

    @Test
    void should_price_ad_hoc_line_as_flat_total_regardless_of_rental_days() {
        UUID customerId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());
        Receipt saved = receiptCaptor.getValue();

        assertThat(saved.getLineItems()).hasSize(1);
        assertThat(saved.getLineItems().get(0).getLineRent()).isEqualTo(500);
        assertThat(saved.getLineItems().get(0).getItem()).isNotNull();
        assertThat(saved.getLineItems().get(0).getItem().getIsAdHoc()).isTrue();
        assertThat(saved.getTotalRent()).isEqualTo(500);
    }

    @Test
    void should_multiply_ad_hoc_flat_price_by_quantity() {
        UUID customerId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 2)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());

        assertThat(receiptCaptor.getValue().getTotalRent()).isEqualTo(1000);
        assertThat(receiptCaptor.getValue().getTotalDeposit()).isEqualTo(2000);
    }

    @Test
    void should_derive_per_day_rate_snapshot_from_flat_price() {
        UUID customerId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());

        // 500 / 3 = 166.67 → 167. This is the late-fee basis only; lineRent stays 500.
        assertThat(receiptCaptor.getValue().getLineItems().get(0).getRateSnapshot()).isEqualTo(167);
    }

    @Test
    void should_floor_derived_per_day_rate_at_one_rupee() {
        UUID customerId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(14);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Cheap Scarf", "S", 5, 0, 1)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());

        // round(5/14) = 0, floored to 1 — items.rate has CHECK (rate > 0), so 0 would fail the insert.
        assertThat(itemCaptor.getValue().getRate()).isEqualTo(1);
    }

    @Test
    void should_not_floor_a_derived_rate_that_rounds_above_one_rupee() {
        UUID customerId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(2);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Cheap Scarf", "S", 5, 0, 1)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());

        // round(5/2) = 3 — proves the ₹1 floor isn't masking a rentalDays=0 stubbing bug that would
        // make every case floor to 1 regardless of the real division.
        assertThat(itemCaptor.getValue().getRate()).isEqualTo(3);
    }

    @Test
    void should_create_hidden_item_row_for_each_ad_hoc_entry() {
        UUID customerId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 2)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        Item created = itemCaptor.getValue();

        assertThat(created.getName()).isEqualTo("Red Sherwani");
        assertThat(created.getSize()).isEqualTo("L");
        assertThat(created.getIsAdHoc()).isTrue();
        assertThat(created.getIsActive()).isTrue();
        assertThat(created.getCategory()).isEqualTo(Item.Category.OTHER);
        assertThat(created.getItemType()).isEqualTo(Item.ItemType.INDIVIDUAL);
        assertThat(created.getQuantity()).isEqualTo(2);
        assertThat(created.getPurchaseRate()).isNull();
    }

    @Test
    void should_preview_ad_hoc_line_without_persisting_an_item() {
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);

        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 2)));

        CheckoutPreviewResponse preview = checkoutService.preview(request);

        assertThat(preview.lineItems()).hasSize(1);
        PreviewLineItem line = preview.lineItems().get(0);
        assertThat(line.itemId()).isNull();
        assertThat(line.itemName()).isEqualTo("Red Sherwani");
        assertThat(line.rate()).isEqualTo(167);
        assertThat(line.lineRent()).isEqualTo(1000);
        assertThat(line.lineDeposit()).isEqualTo(2000);
        assertThat(line.availableQuantity()).isEqualTo(2);
        assertThat(preview.allAvailable()).isTrue();
        assertThat(preview.totalRent()).isEqualTo(1000);

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void should_reject_catalogue_line_that_points_at_an_ad_hoc_item_in_preview() {
        UUID itemId = UUID.randomUUID();
        Item adHocItem = makeItem(itemId, "Walk-in Lehenga", 167, 1000);
        adHocItem.setIsAdHoc(true);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(adHocItem));

        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END, List.of(new CheckoutLineItem(itemId, 1)), List.of());

        assertThatThrownBy(() -> checkoutService.preview(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void should_reject_catalogue_line_that_points_at_an_ad_hoc_item_at_checkout() {
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");
        Item adHocItem = makeItem(itemId, "Walk-in Lehenga", 167, 1000);
        adHocItem.setIsAdHoc(true);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(adHocItem));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(new CheckoutLineItem(itemId, 1)), List.of(), null);

        assertThatThrownBy(() -> checkoutService.createReceipt(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void should_reject_ad_hoc_checkout_from_a_non_owner_role() {
        var executiveAuth = new UsernamePasswordAuthenticationToken(
                "executive", null, List.of(new SimpleGrantedAuthority("ROLE_EXECUTIVE")));
        SecurityContextHolder.getContext().setAuthentication(executiveAuth);

        UUID customerId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        );

        assertThatThrownBy(() -> checkoutService.createReceipt(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("owner");

        verify(itemRepository, never()).save(any(Item.class));
        verify(receiptRepository, never()).save(any(Receipt.class));
    }

    @Test
    void should_allow_catalogue_only_checkout_from_a_non_owner_role() {
        var executiveAuth = new UsernamePasswordAuthenticationToken(
                "executive", null, List.of(new SimpleGrantedAuthority("ROLE_EXECUTIVE")));
        SecurityContextHolder.getContext().setAuthentication(executiveAuth);

        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");
        Item item = makeItem(itemId, "Blue Sherwani", 200, 1000);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(itemId, START, END)).thenReturn(5);
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(itemId, 1)),
                List.of(),
                null
        );

        checkoutService.createReceipt(request);

        verify(receiptRepository).save(any(Receipt.class));
    }

    @Test
    void should_sum_totals_correctly_for_a_mixed_catalogue_and_ad_hoc_cart() {
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Customer customer = makeCustomer(customerId, "Anil", "9800011122");
        Item item = makeItem(itemId, "Blue Sherwani", 200, 1000);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.getAvailableQuantity(itemId, START, END)).thenReturn(5);
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest(
                customerId, START, END,
                List.of(new CheckoutLineItem(itemId, 1)),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        );

        checkoutService.createReceipt(request);

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());
        Receipt saved = receiptCaptor.getValue();

        // catalogue: 200 * 3 days * 1 = 600 rent + 1000 deposit
        // ad-hoc:    500 flat * 1     = 500 rent + 1000 deposit
        assertThat(saved.getLineItems()).hasSize(2);
        assertThat(saved.getTotalRent()).isEqualTo(1100);
        assertThat(saved.getTotalDeposit()).isEqualTo(2000);
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
                List.of(),
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
                List.of(),
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
