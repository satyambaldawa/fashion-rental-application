package com.fashionrental.support;

import com.fashionrental.configuration.LateFeeRule;
import com.fashionrental.customer.Customer;
import com.fashionrental.inventory.Item;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptLineItem;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Central object-creation factory for tests. Builds domain entities with sensible
 * defaults so each test states only what it cares about. Ids and audit timestamps
 * (normally assigned by Hibernate/@PrePersist) are set via reflection so unit tests
 * can work with fully-formed entities without touching a database.
 */
public final class TestData {

    private TestData() {
    }

    public static Customer customer() {
        return customer("Test Customer", "9000000000");
    }

    public static Customer customer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        setId(customer);
        return customer;
    }

    public static Item item() {
        return item("Test Item", 100, 500);
    }

    public static Item item(String name, int rate, int deposit) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(rate);
        item.setDeposit(deposit);
        item.setQuantity(1);
        item.setIsActive(true);
        setId(item);
        return item;
    }

    public static ReceiptLineItem receiptLineItem(Item item, int quantity) {
        ReceiptLineItem line = new ReceiptLineItem();
        line.setItem(item);
        line.setQuantity(quantity);
        line.setRateSnapshot(item.getRate());
        line.setDepositSnapshot(item.getDeposit());
        line.setLineRent(item.getRate() * quantity);
        line.setLineDeposit(item.getDeposit() * quantity);
        setId(line);
        return line;
    }

    public static Receipt receipt(Customer customer, OffsetDateTime start, OffsetDateTime end,
                                  ReceiptLineItem... lines) {
        Receipt receipt = new Receipt();
        receipt.setReceiptNumber("R-TEST-0001");
        receipt.setShareToken("receipt-token");
        receipt.setCustomer(customer);
        receipt.setStartDatetime(start);
        receipt.setEndDatetime(end);
        receipt.setRentalDays(1);
        receipt.setStatus(Receipt.Status.GIVEN);

        int totalRent = 0;
        int totalDeposit = 0;
        for (ReceiptLineItem line : lines) {
            line.setReceipt(receipt);
            receipt.getLineItems().add(line);
            totalRent += line.getLineRent();
            totalDeposit += line.getLineDeposit();
        }
        receipt.setTotalRent(totalRent);
        receipt.setTotalDeposit(totalDeposit);
        receipt.setGrandTotal(totalRent + totalDeposit);
        setId(receipt);
        return receipt;
    }

    public static LateFeeRule lateFeeRule(int fromHours, double multiplier, int sortOrder) {
        LateFeeRule rule = new LateFeeRule();
        rule.setDurationFromHours(fromHours);
        rule.setPenaltyMultiplier(BigDecimal.valueOf(multiplier));
        rule.setSortOrder(sortOrder);
        setId(rule);
        return rule;
    }

    public static Invoice invoice(Invoice.TransactionType type, int finalAmount, int lateFee, int damageCost) {
        Invoice invoice = new Invoice();
        invoice.setTransactionType(type);
        invoice.setFinalAmount(finalAmount);
        invoice.setTotalLateFee(lateFee);
        invoice.setTotalDamageCost(damageCost);
        setId(invoice);
        return invoice;
    }

    /** Sets the (normally @PrePersist-assigned) createdAt so time-window reports can be tested. */
    public static <T> T withCreatedAt(T entity, OffsetDateTime createdAt) {
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        return entity;
    }

    private static void setId(Object entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    }
}
