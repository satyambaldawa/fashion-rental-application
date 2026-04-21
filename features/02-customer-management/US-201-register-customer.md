# US-201: Register New Customer

**Epic:** Customer Management
**Priority:** P0
**Depends On:** SETUP-01, SETUP-02
**Blocks:** US-301 (customer must exist to create a receipt)

---

## User Story

> As a Shop Staff member, I want to register a new customer with their name, phone number, address, and type, so that the customer has a profile that can be referenced on all their rentals.

---

## Acceptance Criteria

- [x] Required fields: name, phone number, customer type
- [x] Optional fields: address, organization name
- [x] Phone number must be unique — if already exists, show error (do not create duplicate)
- [x] When I enter a phone number, system checks for existing customer first (to avoid accidental duplicates)
- [x] Customer type options: Student (requires school name), Professional (requires organization name), Misc (no sub-field)
- [x] New customer is immediately searchable after creation

---

## Backend Implementation

### Entity: `Customer.java`

```java
@Entity
@Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType;

    @Column(name = "organization_name")
    private String organizationName;    // School name for STUDENT, org for PROFESSIONAL

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum CustomerType { STUDENT, PROFESSIONAL, MISC }
}
```

### DTO: `CreateCustomerRequest.java`

```java
public record CreateCustomerRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
    String address,
    @NotNull Customer.CustomerType customerType,
    String organizationName   // Required if STUDENT or PROFESSIONAL
) {}
```

### Service

```java
@Service
@Transactional
public class CustomerService {

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        // Check uniqueness
        if (customerRepository.existsByPhone(request.phone())) {
            throw new ConflictException("A customer with phone " + request.phone() + " already exists.");
        }

        // Validate organization name for typed customers
        if (request.customerType() != Customer.CustomerType.MISC
            && (request.organizationName() == null || request.organizationName().isBlank())) {
            throw new ValidationException("Organization/school name is required for " + request.customerType());
        }

        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setCustomerType(request.customerType());
        customer.setOrganizationName(request.organizationName());
        customer.setIsActive(true);
        customer.setCreatedAt(OffsetDateTime.now());
        customer.setUpdatedAt(OffsetDateTime.now());

        return toResponse(customerRepository.save(customer));
    }
}
```

### Repository

```java
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByPhone(String phone);
    Optional<Customer> findByPhone(String phone);

    @Query("""
        SELECT c FROM Customer c
        WHERE (:phone IS NULL OR c.phone LIKE CONCAT(:phone, '%'))
        AND (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')))
        ORDER BY c.name ASC
        """)
    List<Customer> search(@Param("phone") String phone, @Param("name") String name);
}
```

### API Endpoint

```
POST /api/customers
Body:
{
  "name": "Ramesh Patel",
  "phone": "9876543210",
  "address": "42, Gandhi Nagar, Ahmedabad",
  "customerType": "PROFESSIONAL",
  "organizationName": "Patel & Sons Ltd"
}

Response 201:
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Ramesh Patel",
    "phone": "9876543210",
    "address": "42, Gandhi Nagar, Ahmedabad",
    "customerType": "PROFESSIONAL",
    "organizationName": "Patel & Sons Ltd",
    "createdAt": "2026-04-18T10:00:00+05:30"
  }
}

Response 409: { "success": false, "error": "A customer with phone 9876543210 already exists." }
```

---

## Frontend Implementation

### Register Customer Form

```
Phone Number *       [Input, 10 digits] → [Check] button
                     On check: if found → "Customer already exists, view profile?"
                               if not found → show full registration form

Name *               [Input]
Customer Type *      [Radio: Student / Professional / Misc]
  → If Student:      School Name * [Input]
  → If Professional: Organization Name * [Input]
  → If Misc:         (no sub-field)
Address              [TextArea]

                     [Register Customer]
```

**Workflow:** Staff always starts by entering the phone number and clicking "Check" before filling in the form. This prevents accidental duplicate registration.

```tsx
const [phoneChecked, setPhoneChecked] = useState(false)
const [existingCustomer, setExistingCustomer] = useState<Customer | null>(null)

const checkPhone = async () => {
  const result = await customersApi.search({ phone })
  if (result.length > 0 && result[0].phone === phone) {
    setExistingCustomer(result[0])
  } else {
    setPhoneChecked(true)   // show registration form
  }
}
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| New phone, all fields valid | Customer created, 201 |
| Phone already exists | 409 Conflict with helpful message |
| Phone = "12345" (invalid format) | 400 validation error |
| Type = STUDENT, no school name | 400: "School name required for STUDENT" |
| Type = MISC, no org name | Allowed (Misc has no sub-field) |
| Name blank | 400 validation error |
