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
import com.fashionrental.receipt.model.request.CheckoutPreviewRequest;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.PreviewLineItem;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutService {

    private final ItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final AvailabilityService availabilityService;
    private final PackageComponentRepository packageComponentRepository;
    private final ReceiptRepository receiptRepository;
    private final ReceiptNumberService receiptNumberService;
    private final ShareTokenService shareTokenService;
    private final DateTimeUtil dateTimeUtil;
    private final ReceiptMapper receiptMapper;

    public CheckoutService(
            ItemRepository itemRepository,
            CustomerRepository customerRepository,
            AvailabilityService availabilityService,
            PackageComponentRepository packageComponentRepository,
            ReceiptRepository receiptRepository,
            ReceiptNumberService receiptNumberService,
            ShareTokenService shareTokenService,
            DateTimeUtil dateTimeUtil,
            ReceiptMapper receiptMapper
    ) {
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.availabilityService = availabilityService;
        this.packageComponentRepository = packageComponentRepository;
        this.receiptRepository = receiptRepository;
        this.receiptNumberService = receiptNumberService;
        this.shareTokenService = shareTokenService;
        this.dateTimeUtil = dateTimeUtil;
        this.receiptMapper = receiptMapper;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(CheckoutPreviewRequest request) {
        validateDateRange(request.startDatetime(), request.endDatetime());

        OffsetDateTime start = request.startDatetime();
        OffsetDateTime end = request.endDatetime();
        int rentalDays = dateTimeUtil.calculateRentalDays(start, end);

        List<PreviewLineItem> lineItems = new ArrayList<>();
        List<String> unavailableItems = new ArrayList<>();

        for (var lineItem : request.items()) {
            Item item = itemRepository.findById(lineItem.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + lineItem.itemId()));

            if (!item.getIsActive() || item.getIsAdHoc()) {
                throw new ValidationException("Item '" + item.getName() + "' is no longer available.");
            }

            int available = availabilityService.getAvailableQuantity(lineItem.itemId(), start, end);

            if (available < lineItem.quantity()) {
                unavailableItems.add(item.getName());
            }

            int lineRent = item.getRate() * rentalDays * lineItem.quantity();
            int lineDeposit = item.getDeposit() * lineItem.quantity();

            lineItems.add(new PreviewLineItem(
                    item.getId(),
                    item.getName(),
                    item.getRate(),
                    item.getDeposit(),
                    lineItem.quantity(),
                    rentalDays,
                    lineRent,
                    lineDeposit,
                    available
            ));
        }

        for (AdHocLineItem adHoc : request.adHocItems()) {
            int perDayRate = derivePerDayRate(adHoc.flatPrice(), rentalDays);
            lineItems.add(new PreviewLineItem(
                    null,
                    adHoc.name(),
                    perDayRate,
                    adHoc.deposit(),
                    adHoc.quantity(),
                    rentalDays,
                    adHoc.flatPrice() * adHoc.quantity(),
                    adHoc.deposit() * adHoc.quantity(),
                    adHoc.quantity()
            ));
        }

        int totalRent = lineItems.stream().mapToInt(PreviewLineItem::lineRent).sum();
        int totalDeposit = lineItems.stream().mapToInt(PreviewLineItem::lineDeposit).sum();
        int grandTotal = totalRent + totalDeposit;

        return new CheckoutPreviewResponse(
                unavailableItems.isEmpty(),
                lineItems,
                rentalDays,
                totalRent,
                totalDeposit,
                grandTotal,
                unavailableItems
        );
    }

    @Transactional
    public ReceiptResponse createReceipt(CheckoutRequest request) {
        if (!request.adHocItems().isEmpty() && !hasOwnerRole()) {
            throw new ValidationException("Ad-hoc items can only be checked out by the owner.");
        }

        validateDateRange(request.startDatetime(), request.endDatetime());

        OffsetDateTime start = request.startDatetime();
        OffsetDateTime end = request.endDatetime();
        int rentalDays = dateTimeUtil.calculateRentalDays(start, end);

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.customerId()));

        Receipt receipt = new Receipt();
        receipt.setCustomer(customer);
        receipt.setStartDatetime(start);
        receipt.setEndDatetime(end);
        receipt.setRentalDays(rentalDays);
        receipt.setNotes(request.notes());
        receipt.setStatus(Receipt.Status.GIVEN);
        receipt.setShareToken(shareTokenService.generate());

        List<ReceiptLineItem> lineItems = new ArrayList<>();
        int totalRent = 0;
        int totalDeposit = 0;

        for (var lineItemRequest : request.items()) {
            Item item = itemRepository.findById(lineItemRequest.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + lineItemRequest.itemId()));

            if (!item.getIsActive() || item.getIsAdHoc()) {
                throw new ValidationException("Item '" + item.getName() + "' is no longer available.");
            }

            int available = availabilityService.getAvailableQuantity(lineItemRequest.itemId(), start, end);
            if (available < lineItemRequest.quantity()) {
                throw new ConflictException(
                        "'" + item.getName() + "' no longer has enough units available. Please review your order."
                );
            }

            // Billed line item for the item (or package)
            ReceiptLineItem billedLine = buildLineItem(
                    receipt, item, lineItemRequest.quantity(),
                    item.getRate(), item.getDeposit(), rentalDays);
            lineItems.add(billedLine);
            totalRent    += billedLine.getLineRent();
            totalDeposit += billedLine.getLineDeposit();

            // For packages: add zero-rate reservation lines for each component
            if (item.getItemType() == Item.ItemType.PACKAGE) {
                List<PackageComponent> components = packageComponentRepository.findByPackageItem_Id(item.getId());
                for (PackageComponent comp : components) {
                    int reserveQty = comp.getQuantity() * lineItemRequest.quantity();
                    lineItems.add(buildLineItem(receipt, comp.getComponentItem(), reserveQty, 0, 0, rentalDays));
                }
            }
        }

        for (AdHocLineItem adHoc : request.adHocItems()) {
            Item adHocItem = itemRepository.save(buildAdHocItem(adHoc, rentalDays));
            ReceiptLineItem line = buildAdHocLineItem(receipt, adHocItem, adHoc, rentalDays);
            lineItems.add(line);
            totalRent    += line.getLineRent();
            totalDeposit += line.getLineDeposit();
        }

        receipt.setTotalRent(totalRent);
        receipt.setTotalDeposit(totalDeposit);
        receipt.setGrandTotal(totalRent + totalDeposit);
        receipt.setReceiptNumber(receiptNumberService.generateReceiptNumber());
        receipt.getLineItems().addAll(lineItems);

        Receipt saved = receiptRepository.save(receipt);
        return receiptMapper.toReceiptResponse(saved);
    }

    private ReceiptLineItem buildLineItem(Receipt receipt, Item item, int qty, int rate, int deposit, int rentalDays) {
        ReceiptLineItem li = new ReceiptLineItem();
        li.setReceipt(receipt);
        li.setItem(item);
        li.setQuantity(qty);
        li.setRateSnapshot(rate);
        li.setDepositSnapshot(deposit);
        li.setLineRent(rate * rentalDays * qty);
        li.setLineDeposit(deposit * qty);
        return li;
    }

    private Item buildAdHocItem(AdHocLineItem adHoc, int rentalDays) {
        Item item = new Item();
        item.setName(adHoc.name());
        item.setSize(adHoc.size());
        item.setCategory(Item.Category.OTHER);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(derivePerDayRate(adHoc.flatPrice(), rentalDays));
        item.setDeposit(adHoc.deposit());
        item.setQuantity(adHoc.quantity());
        item.setIsActive(true);
        item.setIsAdHoc(true);
        return item;
    }

    // lineRent is the flat price the staff typed, times quantity — never rate * days * quantity.
    // rateSnapshot is derived only as the late-fee basis (BillingService) and for display; nothing
    // recomputes lineRent from it, so a rupee or two of rounding drift between the two is expected
    // and safe.
    private ReceiptLineItem buildAdHocLineItem(Receipt receipt, Item item, AdHocLineItem adHoc, int rentalDays) {
        ReceiptLineItem li = new ReceiptLineItem();
        li.setReceipt(receipt);
        li.setItem(item);
        li.setQuantity(adHoc.quantity());
        li.setRateSnapshot(derivePerDayRate(adHoc.flatPrice(), rentalDays));
        li.setDepositSnapshot(adHoc.deposit());
        li.setLineRent(adHoc.flatPrice() * adHoc.quantity());
        li.setLineDeposit(adHoc.deposit() * adHoc.quantity());
        return li;
    }

    // Floors the result at ₹1: items.rate has CHECK (rate > 0), so a flat price that rounds down to
    // 0 over a long rental would fail the insert rather than merely mis-price the late fee. Also
    // floors the divisor itself so the safety is co-located with the division, not borrowed from
    // whoever computed rentalDays.
    private int derivePerDayRate(int flatPrice, int rentalDays) {
        int days = Math.max(1, rentalDays);
        return Math.max(1, (int) Math.round((double) flatPrice / days));
    }

    // Content-based check, not route-based: POST /api/receipts also serves ordinary catalogue
    // checkout, open to both roles, so SecurityConfig's URL matchers can't express "OWNER only when
    // this particular request happens to carry adHocItems." Extending ad-hoc checkout to EXECUTIVE
    // later means relaxing or removing this single check — see technical-architecture.md for the
    // tradeoffs that decision should weigh.
    private boolean hasOwnerRole() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
    }

    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) {
            throw new ValidationException("endDatetime must be after startDatetime");
        }
    }
}
