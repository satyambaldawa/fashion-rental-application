package com.fashionrental.inventory;

import jakarta.persistence.*;

import java.util.UUID;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "package_components")
public class PackageComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "package_item_id", nullable = false)
    private Item packageItem;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "component_item_id", nullable = false)
    private Item componentItem;

    @Column(nullable = false)
    private int quantity = 1;

    public UUID getId() { return id; }
    public Item getPackageItem() { return packageItem; }
    public void setPackageItem(Item packageItem) { this.packageItem = packageItem; }
    public Item getComponentItem() { return componentItem; }
    public void setComponentItem(Item componentItem) { this.componentItem = componentItem; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
