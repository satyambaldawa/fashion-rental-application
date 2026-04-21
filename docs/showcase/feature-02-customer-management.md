# Customer Management — Feature Showcase

Stories covered: US-201, US-202

---

## Feature Summary

| Story | Title | Status |
|-------|-------|--------|
| US-201 | Register new customer | Complete |
| US-202 | Search for existing customer | Complete |
| US-203 | Customer rental history | Deferred (P1) |
| US-204 | Edit customer | Deferred (P1) |

---

## Architecture Decisions

### JpaSpecificationExecutor for search (not JPQL)

`CustomerRepository` extends `JpaSpecificationExecutor<Customer>`. The search in `CustomerService.searchCustomers()` builds a `Specification<Customer>` dynamically — prefix match on phone, case-insensitive contains match on name, always filtering `isActive = true`. This avoids the Hibernate 6 / PostgreSQL `lower(bytea)` bug that breaks JPQL `LOWER()` calls when nullable string params are passed.

### Phone-first registration flow

The registration form enforces a phone check before showing the full form. Staff enters the phone number and clicks "Check" — if an exact match exists, the duplicate customer profile is shown instead of the form. This prevents accidental double-registration without a hard-blocking validation.

### Organization name: service-layer validation, not Bean Validation

`@Pattern` on the phone field is handled by Bean Validation (`@Valid`). But the org-name requirement is conditional on `customerType`, which can't be expressed in a single annotation. It's validated in the service layer and throws a `ValidationException` (mapped to 400 by the `GlobalExceptionHandler`).

### activeRentalsCount hardcoded to 0

`CustomerSummaryResponse.activeRentalsCount` is always 0 today. The receipts table exists in the schema but the checkout epic (US-301) hasn't been built yet. Once receipts are implemented, this field will be populated via a COUNT query on `receipt_line_items` filtered by `status = 'GIVEN'`.

### CustomerSearch reusable component

`src/components/common/CustomerSearch.tsx` is a standalone `AutoComplete` component that will be reused during checkout (US-301) for customer selection. It debounces at 300ms, requires 3+ characters, and exposes an `onSelect(CustomerSummary)` callback.

---

## API Surface

### `POST /api/customers` — Register customer

**Request body:**
```json
{
  "name": "Ramesh Patel",
  "phone": "9876543210",
  "address": "42, Gandhi Nagar, Ahmedabad",
  "customerType": "PROFESSIONAL",
  "organizationName": "Patel & Sons Ltd"
}
```

**Validation rules:**
- `name`: required, non-blank
- `phone`: required, matches `^[6-9]\d{9}$` (10-digit Indian mobile)
- `customerType`: required (`STUDENT` / `PROFESSIONAL` / `MISC`)
- `organizationName`: required if type is `STUDENT` or `PROFESSIONAL`; ignored for `MISC`

**Responses:**
- `201` — customer created, returns `CustomerResponse`
- `400` — validation failure (blank name, invalid phone, missing org name)
- `409` — phone already registered

### `GET /api/customers?phone=&name=` — Search customers

- Both params optional; returns empty list if both are blank
- `phone`: prefix match (`LIKE '9876%'`)
- `name`: case-insensitive contains match (`LIKE '%ramesh%'`)
- Max 20 results
- Returns `List<CustomerSummaryResponse>`

### `GET /api/customers/{id}` — Get customer by ID

- Returns `CustomerResponse`
- `404` if not found or `isActive = false`

---

## Test Coverage

### `CustomerServiceTest` — 10 unit tests

| Test | Covers |
|------|--------|
| `should_create_customer_successfully` | Happy path, all fields mapped |
| `should_throw_conflict_when_phone_already_exists` | `ConflictException` on duplicate phone |
| `should_throw_validation_when_student_has_no_school_name` | Org name required for STUDENT |
| `should_throw_validation_when_professional_has_no_org_name` | Org name required for PROFESSIONAL |
| `should_allow_misc_without_org_name` | MISC type has no org name requirement |
| `should_return_empty_list_when_no_search_params` | Both params blank → empty list, no DB call |
| `should_return_search_results_filtered_by_phone` | Spec built correctly for phone filter |
| `should_return_search_results_filtered_by_name` | Spec built correctly for name filter |
| `should_throw_not_found_when_customer_does_not_exist` | `ResourceNotFoundException` on missing id |
| `should_throw_not_found_when_customer_is_inactive` | Inactive customer treated as 404 |

### `CustomerControllerTest` — 8 controller tests (`@WebMvcTest`)

| Test | Covers |
|------|--------|
| `should_return_201_when_customer_created_successfully` | POST happy path, response envelope shape |
| `should_return_409_when_phone_already_exists` | Conflict mapped to 409 |
| `should_return_400_when_phone_format_invalid` | Bean Validation on phone pattern |
| `should_return_400_when_name_is_blank` | Bean Validation on name |
| `should_return_400_when_student_type_missing_org_name` | ValidationException → 400 |
| `should_return_200_with_search_results` | GET search, results returned |
| `should_return_empty_list_when_search_params_missing` | Empty list on blank params |
| `should_return_401_when_not_authenticated` | Unauthenticated request blocked |

---

## Demo Walkthrough

### US-201 — Register a new customer

1. Navigate to **Customers** in the sidebar
2. Click **Register New Customer**
3. Enter phone `9876543210` → click **Check**
4. If no existing customer: the full registration form appears (phone pre-filled, read-only)
5. Fill in: Name = "Ramesh Patel", Type = Professional, Organization = "Patel & Sons Ltd", Address (optional)
6. Click **Register Customer** → success state shows customer name
7. Click **Go to Customers** → customer appears in search

**Duplicate prevention:**
- Repeat steps 3, enter same phone → existing customer card appears with a "View Profile" prompt instead of the form

**Validation:**
- Try phone `12345` → inline error "Enter a valid 10-digit Indian mobile number"
- Select type Student, leave School Name blank → error on submit

### US-202 — Search for existing customer

1. Navigate to **Customers**
2. Type `987` in the search box → results appear after 300ms debounce
3. Type `ramesh` → matches by name (case-insensitive)
4. Type `xyz` → "No customers found" empty state
5. Type `ra` (< 3 chars) → no API call, "Keep typing to search" prompt shown

---

## Known Limitations

- `activeRentalsCount` is always 0 — will be wired up when US-301 (checkout) is implemented
- Customer profile view (detail page per customer) is minimal — full rental history view is US-203 (P1, deferred)
- Edit customer (US-204) is deferred to P1
