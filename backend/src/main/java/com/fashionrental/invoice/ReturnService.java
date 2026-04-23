package com.fashionrental.invoice;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.configuration.LateFeeRule;
import com.fashionrental.configuration.LateFeeRuleRepository;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.invoice.model.response.InvoiceLineItemResponse;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.invoice.model.response.ReturnPreviewLineItem;
import com.fashionrental.invoice.model.response.ReturnPreviewResponse;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptLineItem;
import com.fashionrental.receipt.ReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReturnService {

    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final LateFeeRuleRepository lateFeeRuleRepository;
    private final BillingService billingService;
    private final InvoiceNumberService invoiceNumberService;

    public ReturnService(
            ReceiptRepository receiptRepository,
            InvoiceRepository invoiceRepository,
            LateFeeRuleRepository lateFeeRuleRepository,
            BillingService billingService,
            InvoiceNumberService invoiceNumberService
    ) {
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.lateFeeRuleRepository = lateFeeRuleRepository;
        this.billingService = billingService;
        this.invoiceNumberService = invoiceNumberService;
    }

    @Transactional(readOnly = true)
    public ReturnPreviewResponse previewReturn(UUID receiptId, ProcessReturnRequest request) {
        Receipt receipt = loadActiveReceipt(receiptId);
        List<LateFeeRule> rules = lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc();
        Map<UUID, ReceiptLineItem> lineItemMap = buildLineItemMap(receipt);

        int totalLateFee = 0;
        int totalDamageCost = 0;
        List<ReturnPreviewLineItem> previewLines = new ArrayList<>();

        for (ReturnLineItem returnLine : request.lineItems()) {
            ReceiptLineItem rli = lineItemMap.get(returnLine.receiptLineItemId());
            if (rli == null) continue;

            int lateFee = billingService.calculateLateFee(
                    receipt.getEndDatetime(), request.returnDatetime(),
                    rli.getRateSnapshot(), rli.getQuantity(), rules
            );
            int damage = billingService.calculateDamageCost(rli, returnLine);

            totalLateFee += lateFee;
            totalDamageCost += damage;
            previewLines.add(new ReturnPreviewLineItem(
                    rli.getId(), rli.getItem().getName(), rli.getQuantity(), lateFee, damage
            ));
        }

        return buildPreviewResponse(receipt, totalLateFee, totalDamageCost, previewLines);
    }

    @Transactional
    public InvoiceResponse processReturn(UUID receiptId, ProcessReturnRequest request) {
        Receipt receipt = loadActiveReceipt(receiptId);
        List<LateFeeRule> rules = lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc();
        Map<UUID, ReceiptLineItem> lineItemMap = buildLineItemMap(receipt);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generateInvoiceNumber());
        invoice.setReceipt(receipt);
        invoice.setCustomer(receipt.getCustomer());
        invoice.setReturnDatetime(request.returnDatetime());
        invoice.setTotalRent(receipt.getTotalRent());
        invoice.setTotalDepositCollected(receipt.getTotalDeposit());
        invoice.setPaymentMethod(parsePaymentMethod(request.paymentMethod()));
        invoice.setDamageNotes(request.damageNotes());
        invoice.setNotes(request.notes());

        int totalLateFee = 0;
        int totalDamageCost = 0;

        for (ReturnLineItem returnLine : request.lineItems()) {
            ReceiptLineItem rli = lineItemMap.get(returnLine.receiptLineItemId());
            if (rli == null) continue;

            int lateFee = billingService.calculateLateFee(
                    receipt.getEndDatetime(), request.returnDatetime(),
                    rli.getRateSnapshot(), rli.getQuantity(), rules
            );
            int damage = billingService.calculateDamageCost(rli, returnLine);

            totalLateFee += lateFee;
            totalDamageCost += damage;

            InvoiceLineItem ili = new InvoiceLineItem();
            ili.setInvoice(invoice);
            ili.setReceiptLineItem(rli);
            ili.setItem(rli.getItem());
            ili.setQuantityReturned(rli.getQuantity());
            ili.setIsDamaged(returnLine.isDamaged());
            ili.setDamagePercentage(returnLine.damagePercentage() != null
                    ? BigDecimal.valueOf(returnLine.damagePercentage()) : null);
            ili.setDamageCost(damage);
            ili.setLateFee(lateFee);
            invoice.getLineItems().add(ili);
        }

        int totalDeductions = totalLateFee + totalDamageCost;
        int depositToReturn = Math.max(0, receipt.getTotalDeposit() - totalDeductions);
        int balanceOwed = Math.max(0, totalDeductions - receipt.getTotalDeposit());

        invoice.setTotalLateFee(totalLateFee);
        invoice.setTotalDamageCost(totalDamageCost);
        invoice.setDepositToReturn(depositToReturn);

        if (balanceOwed > 0) {
            invoice.setTransactionType(Invoice.TransactionType.COLLECT);
            invoice.setFinalAmount(balanceOwed);
        } else {
            invoice.setTransactionType(Invoice.TransactionType.REFUND);
            invoice.setFinalAmount(depositToReturn);
        }

        Invoice saved = invoiceRepository.save(invoice);

        receipt.setStatus(Receipt.Status.RETURNED);
        receiptRepository.save(receipt);

        return toInvoiceResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        return toInvoiceResponse(invoice);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private Receipt loadActiveReceipt(UUID receiptId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));
        if (receipt.getStatus() == Receipt.Status.RETURNED) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " has already been returned");
        }
        return receipt;
    }

    private Map<UUID, ReceiptLineItem> buildLineItemMap(Receipt receipt) {
        return receipt.getLineItems().stream()
                .collect(Collectors.toMap(ReceiptLineItem::getId, Function.identity()));
    }

    private ReturnPreviewResponse buildPreviewResponse(
            Receipt receipt, int totalLateFee, int totalDamageCost,
            List<ReturnPreviewLineItem> lines
    ) {
        int totalDeductions = totalLateFee + totalDamageCost;
        int depositToReturn = Math.max(0, receipt.getTotalDeposit() - totalDeductions);
        int balanceOwed = Math.max(0, totalDeductions - receipt.getTotalDeposit());
        String txType = balanceOwed > 0 ? "COLLECT" : "REFUND";
        int finalAmount = balanceOwed > 0 ? balanceOwed : depositToReturn;
        return new ReturnPreviewResponse(
                totalLateFee, totalDamageCost, totalDeductions,
                depositToReturn, finalAmount, txType, lines
        );
    }

    private Invoice.PaymentMethod parsePaymentMethod(String value) {
        if (value == null) return Invoice.PaymentMethod.CASH;
        try {
            return Invoice.PaymentMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Invoice.PaymentMethod.CASH;
        }
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        List<InvoiceLineItemResponse> lineItems = invoice.getLineItems().stream()
                .map(this::toLineItemResponse)
                .toList();

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getReceipt().getId(),
                invoice.getReceipt().getReceiptNumber(),
                invoice.getCustomer().getId(),
                invoice.getCustomer().getName(),
                invoice.getCustomer().getPhone(),
                invoice.getReturnDatetime(),
                invoice.getTotalRent(),
                invoice.getTotalDepositCollected(),
                invoice.getTotalLateFee(),
                invoice.getTotalDamageCost(),
                invoice.getDepositToReturn(),
                invoice.getFinalAmount(),
                invoice.getTransactionType().name(),
                invoice.getPaymentMethod().name(),
                invoice.getDamageNotes(),
                invoice.getNotes(),
                lineItems,
                invoice.getCreatedAt()
        );
    }

    private InvoiceLineItemResponse toLineItemResponse(InvoiceLineItem ili) {
        ReceiptLineItem rli = ili.getReceiptLineItem();
        return new InvoiceLineItemResponse(
                ili.getId(),
                ili.getItem().getId(),
                ili.getItem().getName(),
                ili.getItem().getSize(),
                ili.getItem().getCategory() != null ? ili.getItem().getCategory().name() : null,
                ili.getQuantityReturned(),
                rli.getRateSnapshot(),
                rli.getDepositSnapshot(),
                ili.getIsDamaged(),
                ili.getDamagePercentage() != null ? ili.getDamagePercentage().doubleValue() : null,
                ili.getDamageCost(),
                ili.getLateFee()
        );
    }
}
