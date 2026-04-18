# US-204: Edit Customer Details

**Epic:** Customer Management
**Priority:** P1
**Depends On:** US-201
**Blocks:** Nothing

---

## User Story

> As a Shop Owner, I want to edit a customer's contact details, so that records stay accurate when a customer's address or phone number changes.

---

## Acceptance Criteria

- [ ] All fields editable: name, phone, address, customer type, organization name
- [ ] Phone number update checks uniqueness against other customers (not self)
- [ ] `updated_at` timestamp updated on save

---

## Backend Implementation

### DTO: `UpdateCustomerRequest.java`

```java
public record UpdateCustomerRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$") String phone,
    String address,
    @NotNull Customer.CustomerType customerType,
    String organizationName
) {}
```

### Service

```java
@Transactional
public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request) {
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

    // Check phone uniqueness (exclude self)
    customerRepository.findByPhone(request.phone()).ifPresent(existing -> {
        if (!existing.getId().equals(id)) {
            throw new ConflictException("Phone " + request.phone() + " is already used by another customer.");
        }
    });

    customer.setName(request.name());
    customer.setPhone(request.phone());
    customer.setAddress(request.address());
    customer.setCustomerType(request.customerType());
    customer.setOrganizationName(request.organizationName());
    customer.setUpdatedAt(OffsetDateTime.now());

    return toResponse(customerRepository.save(customer));
}
```

### API Endpoint

```
PUT /api/customers/{id}
Body: same as UpdateCustomerRequest

Response 200: updated CustomerResponse
Response 409: phone already used by another customer
Response 404: customer not found
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Change address | Updated, 200 |
| Change phone to own current phone | Allowed (no conflict with self) |
| Change phone to another customer's phone | 409 Conflict |
| Change type from STUDENT to MISC, no org name | Allowed |
| Change type from MISC to PROFESSIONAL, no org name | 400: org name required |
