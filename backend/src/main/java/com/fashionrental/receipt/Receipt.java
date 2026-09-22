package com.fashionrental.receipt;

import com.fashionrental.customer.Customer;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "receipts")
@NamedEntityGraph(
        name = Receipt.SUMMARY_ENTITY_GRAPH,
        attributeNodes = {
                @NamedAttributeNode("customer"),
                @NamedAttributeNode(value = "lineItems", subgraph = "lineItems.item")
        },
        subgraphs = @NamedSubgraph(
                name = "lineItems.item",
                attributeNodes = @NamedAttributeNode("item")
        )
)
public class Receipt {

    /**
     * Fetches the customer, line items, and each line item's item alongside the receipt in
     * one query. Used by the finders behind the Active Rentals summary — the database round
     * trip is cross-region in production, so per-row lazy loading there costs seconds.
     */
    public static final String SUMMARY_ENTITY_GRAPH = "Receipt.summary";

    public enum Status {
        GIVEN, RETURNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false)
    private String receiptNumber;

    @Column(name = "share_token", nullable = false, unique = true, length = 12)
    private String shareToken;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "start_datetime", nullable = false)
    private OffsetDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private OffsetDateTime endDatetime;

    @Column(name = "rental_days", nullable = false)
    private int rentalDays;

    @Column(name = "total_rent", nullable = false)
    private int totalRent;

    @Column(name = "total_deposit", nullable = false)
    private int totalDeposit;

    @Column(name = "coupon_code", length = 32)
    private String couponCode;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount = 0;

    @Column(name = "grand_total", nullable = false)
    private int grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.GIVEN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "receipt", cascade = ALL, fetch = LAZY, orphanRemoval = true)
    private List<ReceiptLineItem> lineItems = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public OffsetDateTime getStartDatetime() { return startDatetime; }
    public void setStartDatetime(OffsetDateTime startDatetime) { this.startDatetime = startDatetime; }
    public OffsetDateTime getEndDatetime() { return endDatetime; }
    public void setEndDatetime(OffsetDateTime endDatetime) { this.endDatetime = endDatetime; }
    public int getRentalDays() { return rentalDays; }
    public void setRentalDays(int rentalDays) { this.rentalDays = rentalDays; }
    public int getTotalRent() { return totalRent; }
    public void setTotalRent(int totalRent) { this.totalRent = totalRent; }
    public int getTotalDeposit() { return totalDeposit; }
    public void setTotalDeposit(int totalDeposit) { this.totalDeposit = totalDeposit; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public int getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(int discountAmount) { this.discountAmount = discountAmount; }
    public int getGrandTotal() { return grandTotal; }
    public void setGrandTotal(int grandTotal) { this.grandTotal = grandTotal; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<ReceiptLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<ReceiptLineItem> lineItems) { this.lineItems = lineItems; }
}
