package com.fashionrental.configuration;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

// Single-row table (id is always 1, seeded by migration): shop-wide settings that are not a
// collection of entities like coupons or late fee rules.
@Entity
@Table(name = "shop_settings")
public class ShopSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Short id;

    @Column(name = "upi_vpa", length = 256)
    private String upiVpa;

    @Column(name = "upi_payee_name", length = 100)
    private String upiPayeeName;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isUpiConfigured() {
        return upiVpa != null;
    }

    public Short getId() { return id; }
    public String getUpiVpa() { return upiVpa; }
    public String getUpiPayeeName() { return upiPayeeName; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Short id) { this.id = id; }
    public void setUpiVpa(String upiVpa) { this.upiVpa = upiVpa; }
    public void setUpiPayeeName(String upiPayeeName) { this.upiPayeeName = upiPayeeName; }
}
