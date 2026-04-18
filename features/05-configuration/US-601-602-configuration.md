# US-601 / US-602: Configuration — Late Fee Rules & Deposit Settings

**Epic:** Pricing & Configuration
**Priority:** P0
**Depends On:** SETUP-01 (V1 migration seeds default rules)
**Blocks:** US-401 (late fee engine reads these rules)

---

## User Stories

**US-601:** Configure late fee rules with time-range tiers and penalty multipliers.
**US-602:** Define and update the deposit amount per item (handled as part of US-104/105 item management, but this story ensures rules don't affect existing receipts).

---

## Acceptance Criteria (US-601)

- [ ] Owner can view all current late fee tiers
- [ ] Owner can update the multiplier for any tier
- [ ] Owner can add new tiers or remove existing ones
- [ ] Changes apply to **future** return calculations only — already generated invoices are not affected
- [ ] System must always have at least one active rule
- [ ] Default rules are seeded by V1 migration (owner configures actual values before go-live)

---

## Backend Implementation

### Entity: `LateFeeRule.java`

```java
@Entity
@Table(name = "late_fee_rules")
public class LateFeeRule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "duration_from_hours", nullable = false)
    private Integer durationFromHours;    // inclusive

    @Column(name = "duration_to_hours")
    private Integer durationToHours;      // exclusive; null = infinity

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
}
```

### DTOs

```java
public record LateFeeRuleResponse(
    UUID id,
    int durationFromHours,
    Integer durationToHours,   // null = open-ended (2+ days)
    BigDecimal penaltyMultiplier,
    int sortOrder,
    boolean isActive,
    String label               // computed: "0–3 hrs", "3–6 hrs", "1–2 days", "2+ days"
) {}

public record UpdateLateFeeRulesRequest(
    @NotEmpty List<LateFeeRuleItem> rules
) {}

public record LateFeeRuleItem(
    UUID id,                   // null for new rules
    @NotNull @Min(0) Integer durationFromHours,
    Integer durationToHours,
    @NotNull @DecimalMin("0.1") BigDecimal penaltyMultiplier,
    @NotNull Integer sortOrder,
    boolean isActive
) {}
```

### Service

```java
@Service
@Transactional
public class ConfigService {

    public List<LateFeeRuleResponse> getLateFeeRules() {
        return lateFeeRuleRepository.findAllByOrderBySortOrderAsc()
            .stream().map(this::toResponse).toList();
    }

    public List<LateFeeRuleResponse> updateLateFeeRules(UpdateLateFeeRulesRequest request) {
        // Validate: no overlapping ranges, at least one active rule
        validateRules(request.rules());

        // Deactivate all existing, then upsert new set
        // (simpler than partial update; rules are few in number)
        lateFeeRuleRepository.deactivateAll();

        List<LateFeeRule> saved = request.rules().stream().map(item -> {
            LateFeeRule rule = item.id() != null
                ? lateFeeRuleRepository.findById(item.id()).orElse(new LateFeeRule())
                : new LateFeeRule();
            rule.setDurationFromHours(item.durationFromHours());
            rule.setDurationToHours(item.durationToHours());
            rule.setPenaltyMultiplier(item.penaltyMultiplier());
            rule.setSortOrder(item.sortOrder());
            rule.setIsActive(item.isActive());
            rule.setUpdatedAt(OffsetDateTime.now());
            if (rule.getCreatedAt() == null) rule.setCreatedAt(OffsetDateTime.now());
            return lateFeeRuleRepository.save(rule);
        }).toList();

        return saved.stream().map(this::toResponse).toList();
    }

    private void validateRules(List<LateFeeRuleItem> rules) {
        long activeCount = rules.stream().filter(LateFeeRuleItem::isActive).count();
        if (activeCount == 0) throw new ValidationException("At least one late fee rule must be active.");

        // Check for overlapping ranges
        List<LateFeeRuleItem> active = rules.stream()
            .filter(LateFeeRuleItem::isActive)
            .sorted(Comparator.comparingInt(LateFeeRuleItem::durationFromHours))
            .toList();

        for (int i = 0; i < active.size() - 1; i++) {
            LateFeeRuleItem curr = active.get(i);
            LateFeeRuleItem next = active.get(i + 1);
            if (curr.durationToHours() == null || curr.durationToHours() > next.durationFromHours()) {
                throw new ValidationException("Late fee rule ranges must not overlap.");
            }
        }
    }
}
```

### API Endpoints

```
GET /api/config/late-fee-rules
Response 200:
{
  "success": true,
  "data": [
    { "id": "uuid", "durationFromHours": 0,  "durationToHours": 3,  "penaltyMultiplier": 0.50, "sortOrder": 1, "isActive": true, "label": "0–3 hrs" },
    { "id": "uuid", "durationFromHours": 3,  "durationToHours": 6,  "penaltyMultiplier": 0.75, "sortOrder": 2, "isActive": true, "label": "3–6 hrs" },
    { "id": "uuid", "durationFromHours": 6,  "durationToHours": 24, "penaltyMultiplier": 1.00, "sortOrder": 3, "isActive": true, "label": "6 hrs–1 day" },
    { "id": "uuid", "durationFromHours": 24, "durationToHours": 48, "penaltyMultiplier": 1.50, "sortOrder": 4, "isActive": true, "label": "1–2 days" },
    { "id": "uuid", "durationFromHours": 48, "durationToHours": null,"penaltyMultiplier": 2.00, "sortOrder": 5, "isActive": true, "label": "2+ days" }
  ]
}

PUT /api/config/late-fee-rules
Body: UpdateLateFeeRulesRequest
Response 200: updated list
Response 400: overlapping ranges or no active rules
```

---

## Frontend Implementation

### Settings Page — Late Fee Configuration

```
Late Fee Rules
──────────────
⚠️ These values are applied to all future return calculations.
   Changes do not affect invoices that have already been generated.

[+ Add Tier]

┌─────────────────────────────────────────────────────────┐
│ From (hrs)  To (hrs)    Multiplier    Label Preview     │
│    0           3           0.50x      0–3 hrs late     │
│    3           6           0.75x      3–6 hrs late     │
│    6          24           1.00x      6 hrs–1 day      │
│   24          48           1.50x      1–2 days late    │
│   48          ∞            2.00x      2+ days late     │
└─────────────────────────────────────────────────────────┘

[Save Rules]
```

**Validation before save:**
- No two rows with overlapping hour ranges
- At least one row must be active
- Multiplier must be > 0

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Get rules on fresh install | Returns 5 default rules seeded by V1 migration |
| Update multiplier for 0-3hr tier from 0.5 to 0.75 | Saved, future returns use 0.75 |
| Remove all rules | 400: at least one rule required |
| Overlapping ranges (0-6hr and 3-12hr) | 400: ranges must not overlap |
| Change rules, then process return | New return uses updated rules |
| Invoice already generated | Existing invoices unchanged (amount is stored on invoice, not recalculated) |
