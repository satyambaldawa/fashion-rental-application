package com.fashionrental.reporting;

import com.fashionrental.customer.model.response.CustomerReceiptInvoiceResponse;
import com.fashionrental.customer.model.response.CustomerReceiptItemResponse;
import com.fashionrental.customer.model.response.CustomerReceiptResponse;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerHistoryService {

    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;

    public CustomerHistoryService(ReceiptRepository receiptRepository, InvoiceRepository invoiceRepository) {
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public CustomerHistoryData getHistory(UUID customerId) {
        var receipts = receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);

        Map<UUID, Invoice> invoiceByReceiptId = receipts.isEmpty()
                ? Map.of()
                : invoiceRepository.findByReceipt_IdIn(receipts.stream().map(Receipt::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(invoice -> invoice.getReceipt().getId(), Function.identity()));

        int outstandingDeposit = receipts.stream()
                .filter(r -> r.getStatus() == Receipt.Status.GIVEN)
                .mapToInt(Receipt::getTotalDeposit)
                .sum();

        var receiptResponses = receipts.stream()
                .map(r -> toReceiptResponse(r, invoiceByReceiptId.get(r.getId())))
                .toList();

        return new CustomerHistoryData(outstandingDeposit, receiptResponses);
    }

    private CustomerReceiptResponse toReceiptResponse(Receipt receipt, Invoice invoice) {
        var items = receipt.getLineItems().stream()
                .filter(li -> li.getRateSnapshot() > 0 || li.getDepositSnapshot() > 0)
                .map(li -> new CustomerReceiptItemResponse(li.getItem().getName(), li.getQuantity()))
                .toList();

        return new CustomerReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getStatus().name(),
                receipt.getStartDatetime(),
                receipt.getEndDatetime(),
                receipt.getTotalRent(),
                receipt.getTotalDeposit(),
                receipt.getGrandTotal(),
                items,
                toInvoiceResponse(invoice)
        );
    }

    private CustomerReceiptInvoiceResponse toInvoiceResponse(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        return new CustomerReceiptInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getFinalAmount(),
                invoice.getTransactionType().name()
        );
    }
}
