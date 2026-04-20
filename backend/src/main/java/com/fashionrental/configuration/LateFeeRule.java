package com.fashionrental.configuration;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "late_fee_rules")
public class LateFeeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "duration_from_hours", nullable = false)
    private Integer durationFromHours;

    @Column(name = "duration_to_hours")
    private Integer durationToHours;

    @Column(name = "penalty_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal penaltyMultiplier;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public Integer getDurationFromHours() { return durationFromHours; }
    public void setDurationFromHours(Integer durationFromHours) { this.durationFromHours = durationFromHours; }
    public Integer getDurationToHours() { return durationToHours; }
    public void setDurationToHours(Integer durationToHours) { this.durationToHours = durationToHours; }
    public BigDecimal getPenaltyMultiplier() { return penaltyMultiplier; }
    public void setPenaltyMultiplier(BigDecimal penaltyMultiplier) { this.penaltyMultiplier = penaltyMultiplier; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
