package com.fashionrental.invoice;

import com.fashionrental.customer.Customer;
import com.fashionrental.receipt.Receipt;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "invoices")
public class Invoice {

    public enum TransactionType { COLLECT, REFUND }
    public enum PaymentMethod { CASH, UPI, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "return_datetime", nullable = false)
    private OffsetDateTime returnDatetime;

    @Column(name = "total_rent", nullable = false)
    private int totalRent;

    @Column(name = "total_deposit_collected", nullable = false)
    private int totalDepositCollected;

    @Column(name = "total_late_fee", nullable = false)
    private int totalLateFee;

    @Column(name = "total_damage_cost", nullable = false)
    private int totalDamageCost;

    @Column(name = "deposit_to_return", nullable = false)
    private int depositToReturn;

    @Column(name = "final_amount", nullable = false)
    private int finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "damage_notes", columnDefinition = "TEXT")
    private String damageNotes;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "invoice", cascade = ALL, fetch = LAZY, orphanRemoval = true)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public OffsetDateTime getReturnDatetime() { return returnDatetime; }
    public void setReturnDatetime(OffsetDateTime returnDatetime) { this.returnDatetime = returnDatetime; }
    public int getTotalRent() { return totalRent; }
    public void setTotalRent(int totalRent) { this.totalRent = totalRent; }
    public int getTotalDepositCollected() { return totalDepositCollected; }
    public void setTotalDepositCollected(int totalDepositCollected) { this.totalDepositCollected = totalDepositCollected; }
    public int getTotalLateFee() { return totalLateFee; }
    public void setTotalLateFee(int totalLateFee) { this.totalLateFee = totalLateFee; }
    public int getTotalDamageCost() { return totalDamageCost; }
    public void setTotalDamageCost(int totalDamageCost) { this.totalDamageCost = totalDamageCost; }
    public int getDepositToReturn() { return depositToReturn; }
    public void setDepositToReturn(int depositToReturn) { this.depositToReturn = depositToReturn; }
    public int getFinalAmount() { return finalAmount; }
    public void setFinalAmount(int finalAmount) { this.finalAmount = finalAmount; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getDamageNotes() { return damageNotes; }
    public void setDamageNotes(String damageNotes) { this.damageNotes = damageNotes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<InvoiceLineItem> getLineItems() { return lineItems; }
}
