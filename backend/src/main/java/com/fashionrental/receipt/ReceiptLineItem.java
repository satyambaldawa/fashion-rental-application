package com.fashionrental.receipt;

import com.fashionrental.inventory.Item;
import jakarta.persistence.*;

import java.util.UUID;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "receipt_line_items")
public class ReceiptLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "rate_snapshot", nullable = false)
    private int rateSnapshot;

    @Column(name = "deposit_snapshot", nullable = false)
    private int depositSnapshot;

    @Column(name = "line_rent", nullable = false)
    private int lineRent;

    @Column(name = "line_deposit", nullable = false)
    private int lineDeposit;

    public UUID getId() { return id; }
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getRateSnapshot() { return rateSnapshot; }
    public void setRateSnapshot(int rateSnapshot) { this.rateSnapshot = rateSnapshot; }
    public int getDepositSnapshot() { return depositSnapshot; }
    public void setDepositSnapshot(int depositSnapshot) { this.depositSnapshot = depositSnapshot; }
    public int getLineRent() { return lineRent; }
    public void setLineRent(int lineRent) { this.lineRent = lineRent; }
    public int getLineDeposit() { return lineDeposit; }
    public void setLineDeposit(int lineDeposit) { this.lineDeposit = lineDeposit; }
}
