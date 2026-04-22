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
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.PreviewLineItem;
import com.fashionrental.receipt.model.response.ReceiptLineItemResponse;
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
    private final ReceiptRepository receiptRepository;
    private final ReceiptNumberService receiptNumberService;
    private final DateTimeUtil dateTimeUtil;

    public CheckoutService(
            ItemRepository itemRepository,
            CustomerRepository customerRepository,
            AvailabilityService availabilityService,
            ReceiptRepository receiptRepository,
            ReceiptNumberService receiptNumberService,
            DateTimeUtil dateTimeUtil
    ) {
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.availabilityService = availabilityService;
        this.receiptRepository = receiptRepository;
        this.receiptNumberService = receiptNumberService;
        this.dateTimeUtil = dateTimeUtil;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(CheckoutRequest request) {
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

            int lineRent = item.getRate() * rentalDays * lineItemRequest.quantity();
            int lineDeposit = item.getDeposit() * lineItemRequest.quantity();

            ReceiptLineItem lineItem = new ReceiptLineItem();
            lineItem.setReceipt(receipt);
            lineItem.setItem(item);
            lineItem.setQuantity(lineItemRequest.quantity());
            lineItem.setRateSnapshot(item.getRate());
            lineItem.setDepositSnapshot(item.getDeposit());
            lineItem.setLineRent(lineRent);
            lineItem.setLineDeposit(lineDeposit);

            lineItems.add(lineItem);
            totalRent += lineRent;
            totalDeposit += lineDeposit;
        }

        receipt.setTotalRent(totalRent);
        receipt.setTotalDeposit(totalDeposit);
        receipt.setGrandTotal(totalRent + totalDeposit);
        receipt.setReceiptNumber(receiptNumberService.generateReceiptNumber());
        receipt.getLineItems().addAll(lineItems);

        Receipt saved = receiptRepository.save(receipt);
        return toReceiptResponse(saved);
    }

    private void validateDateRange(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) {
            throw new ValidationException("endDatetime must be after startDatetime");
        }
    }

    ReceiptResponse toReceiptResponse(Receipt receipt) {
        List<ReceiptLineItemResponse> lineItemResponses = receipt.getLineItems().stream()
                .map(li -> new ReceiptLineItemResponse(
                        li.getId(),
                        li.getItem().getId(),
                        li.getItem().getName(),
                        li.getQuantity(),
                        li.getRateSnapshot(),
                        li.getDepositSnapshot(),
                        li.getLineRent(),
                        li.getLineDeposit()
                ))
                .toList();

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getCustomer().getId(),
                receipt.getCustomer().getName(),
                receipt.getCustomer().getPhone(),
                receipt.getStartDatetime(),
                receipt.getEndDatetime(),
                receipt.getRentalDays(),
                receipt.getTotalRent(),
                receipt.getTotalDeposit(),
                receipt.getGrandTotal(),
                receipt.getStatus().name(),
                receipt.getNotes(),
                lineItemResponses,
                receipt.getCreatedAt()
        );
    }
}
