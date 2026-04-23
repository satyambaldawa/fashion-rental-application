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
import com.fashionrental.receipt.model.request.CheckoutPreviewRequest;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.PreviewLineItem;
import com.fashionrental.receipt.model.response.ReceiptResponse;
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
    private final DateTimeUtil dateTimeUtil;
    private final ReceiptMapper receiptMapper;

    public CheckoutService(
            ItemRepository itemRepository,
            CustomerRepository customerRepository,
            AvailabilityService availabilityService,
            PackageComponentRepository packageComponentRepository,
            ReceiptRepository receiptRepository,
            ReceiptNumberService receiptNumberService,
            DateTimeUtil dateTimeUtil,
            ReceiptMapper receiptMapper
    ) {
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.availabilityService = availabilityService;
        this.packageComponentRepository = packageComponentRepository;
        this.receiptRepository = receiptRepository;
        this.receiptNumberService = receiptNumberService;
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

        List<ReceiptLineItem> lineItems = new ArrayList<>();
        int totalRent = 0;
        int totalDeposit = 0;

        for (var lineItemRequest : request.items()) {
            Item item = itemRepository.findById(lineItemRequest.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + lineItemRequest.itemId()));

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

    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) {
            throw new ValidationException("endDatetime must be after startDatetime");
        }
    }
}
