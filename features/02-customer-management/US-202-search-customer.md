# US-202: Search for Existing Customer

**Epic:** Customer Management
**Priority:** P0
**Depends On:** US-201
**Blocks:** US-301 (customer selection during checkout)

---

## User Story

> As a Shop Staff member, I want to search for an existing customer by phone number or name, so that I can retrieve their profile quickly when they arrive for a rental or return.

---

## Acceptance Criteria

- [x] Search by full or partial phone number (prefix match)
- [x] Search by partial name (contains match, case-insensitive)
- [x] Results appear immediately on input (debounced, 300ms)
- [x] Selecting a customer shows their full profile
- [x] Customer lookup by phone: < 5 seconds (easily < 500ms with index)

---

## Backend Implementation

Uses the `search` query already defined in `CustomerRepository` (US-201).

### API Endpoint

```
GET /api/customers?phone=98765&name=ramesh

Response 200:
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Ramesh Patel",
      "phone": "9876543210",
      "customerType": "PROFESSIONAL",
      "organizationName": "Patel & Sons Ltd",
      "activeRentalsCount": 1    // how many items currently out
    }
  ]
}
```

**Limit:** Return maximum 20 results. Staff should narrow the search if too many results.

### Service

```java
@Transactional(readOnly = true)
public List<CustomerSummaryResponse> searchCustomers(String phone, String name) {
    if ((phone == null || phone.isBlank()) && (name == null || name.isBlank())) {
        return List.of();  // Return empty if no search term
    }
    List<Customer> customers = customerRepository.search(
        phone != null && !phone.isBlank() ? phone : null,
        name != null && !name.isBlank() ? name : null
    );
    return customers.stream()
        .limit(20)
        .map(this::toSummaryResponse)
        .toList();
}
```

---

## Frontend Implementation

### Customer Search Component (reused in checkout too)

```tsx
// src/components/common/CustomerSearch.tsx
// Used in: CustomerPage, CheckoutPage

export function CustomerSearch({ onSelect }: { onSelect: (c: Customer) => void }) {
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebounce(query, 300)

  const { data: results = [] } = useQuery({
    queryKey: ['customers', 'search', debouncedQuery],
    queryFn: () => customersApi.search(debouncedQuery),
    enabled: debouncedQuery.length >= 3,
  })

  return (
    <AutoComplete
      options={results.map(c => ({
        value: c.id,
        label: `${c.name} — ${c.phone}`
      }))}
      onSearch={setQuery}
      onSelect={(id) => onSelect(results.find(c => c.id === id)!)}
      placeholder="Search by phone or name..."
    />
  )
}
```

### Customer Profile View

After selecting a customer, show:
```
Ramesh Patel                                [Edit]
📱 9876543210
🏢 Patel & Sons Ltd (Professional)
📍 42, Gandhi Nagar, Ahmedabad
Member since: Apr 2026

Active Rentals: 1 item currently out
[View All Rentals]
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Search "9876" | Returns all customers whose phone starts with 9876 |
| Search "ramesh" | Returns customers with "ramesh" in their name (case-insensitive) |
| Search "xyz" (no match) | Empty list, no error |
| Search less than 3 chars | No API call (too short to be useful) |
| Two customers, same name | Both returned; distinguished by phone |
