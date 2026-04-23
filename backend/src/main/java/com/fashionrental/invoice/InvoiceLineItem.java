package com.fashionrental.invoice;

import com.fashionrental.inventory.Item;
import com.fashionrental.receipt.ReceiptLineItem;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "receipt_line_item_id", nullable = false)
    private ReceiptLineItem receiptLineItem;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity_returned", nullable = false)
    private int quantityReturned;

    @Column(name = "is_damaged", nullable = false)
    private boolean isDamaged = false;

    @Column(name = "damage_percentage", precision = 5, scale = 2)
    private BigDecimal damagePercentage;

    @Column(name = "damage_cost", nullable = false)
    private int damageCost;

    @Column(name = "late_fee", nullable = false)
    private int lateFee;

    public UUID getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public ReceiptLineItem getReceiptLineItem() { return receiptLineItem; }
    public void setReceiptLineItem(ReceiptLineItem receiptLineItem) { this.receiptLineItem = receiptLineItem; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public int getQuantityReturned() { return quantityReturned; }
    public void setQuantityReturned(int quantityReturned) { this.quantityReturned = quantityReturned; }
    public boolean getIsDamaged() { return isDamaged; }
    public void setIsDamaged(boolean isDamaged) { this.isDamaged = isDamaged; }
    public BigDecimal getDamagePercentage() { return damagePercentage; }
    public void setDamagePercentage(BigDecimal damagePercentage) { this.damagePercentage = damagePercentage; }
    public int getDamageCost() { return damageCost; }
    public void setDamageCost(int damageCost) { this.damageCost = damageCost; }
    public int getLateFee() { return lateFee; }
    public void setLateFee(int lateFee) { this.lateFee = lateFee; }
}
