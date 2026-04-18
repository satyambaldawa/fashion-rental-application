# US-102: Search and Filter Items

**Epic:** Inventory Management
**Priority:** P0
**Depends On:** US-101 (reuses same endpoint + page)
**Blocks:** US-301 (item search during checkout)

---

## User Story

> As a Shop Staff member, I want to search for items in inventory by name, category, or size, so that I can find specific items quickly without scrolling through the full catalog.

---

## Acceptance Criteria

- [ ] A search bar at the top of the inventory page accepts free text; results update on submit (not keystroke, to avoid excessive API calls)
- [ ] Category filter: dropdown with options — All, Costume, Accessories, Pagdi, Dress, Ornaments
- [ ] Size filter: free text input (sizes vary per item, not a fixed enum)
- [ ] Filters can be combined (e.g., category=COSTUME + search="sherwani")
- [ ] If no results match, show "No items found" with the applied search terms
- [ ] Clearing all filters restores the full list

---

## Backend Implementation

Same endpoint as US-101 with additional query params. The `searchItems` JPQL query in `ItemRepository` already handles all three filter params (see US-101).

```
GET /api/items?search=sherwani&category=COSTUME&itemSize=M&page=0&size=20
```

No additional backend changes needed beyond US-101.

---

## Frontend Implementation

### Search Bar Component

Add a filter toolbar above the item grid in `InventoryPage.tsx`:

```tsx
// src/pages/inventory/InventoryFilters.tsx
interface Filters {
  search: string
  category: string | undefined
  itemSize: string
}

export function InventoryFilters({ filters, onChange }: {
  filters: Filters
  onChange: (f: Filters) => void
}) {
  const [localSearch, setLocalSearch] = useState(filters.search)

  return (
    <Space wrap>
      <Input.Search
        placeholder="Search by name..."
        value={localSearch}
        onChange={(e) => setLocalSearch(e.target.value)}
        onSearch={(val) => onChange({ ...filters, search: val })}
        allowClear
        style={{ width: 280 }}
      />
      <Select
        placeholder="All Categories"
        allowClear
        style={{ width: 180 }}
        value={filters.category}
        onChange={(val) => onChange({ ...filters, category: val })}
        options={[
          { value: 'COSTUME', label: 'Costume' },
          { value: 'ACCESSORIES', label: 'Accessories' },
          { value: 'PAGDI', label: 'Pagdi' },
          { value: 'DRESS', label: 'Dress' },
          { value: 'ORNAMENTS', label: 'Ornaments' },
        ]}
      />
      <Input
        placeholder="Size (e.g., M, L, 38)"
        value={filters.itemSize}
        onChange={(e) => onChange({ ...filters, itemSize: e.target.value })}
        onPressEnter={() => onChange({ ...filters })}
        style={{ width: 140 }}
        allowClear
      />
    </Space>
  )
}
```

### State in InventoryPage

```tsx
const [filters, setFilters] = useState<Filters>({ search: '', category: undefined, itemSize: '' })
const [page, setPage] = useState(0)

// Reset to page 0 when filters change
const handleFiltersChange = (newFilters: Filters) => {
  setFilters(newFilters)
  setPage(0)
}

const { data, isLoading } = useQuery({
  queryKey: ['items', filters, page],
  queryFn: () => itemsApi.list({ ...filters, page })
})
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Search "sherwani" | Shows only items whose name contains "sherwani" (case-insensitive) |
| Category = COSTUME | Shows only costume items |
| Search "blue" + Category = DRESS | Shows only dresses with "blue" in name |
| Search "zzz" (no match) | Shows "No items found" message |
| Clear search after filtering | Returns full list |
| Size filter "M" | Shows items with size containing "M" (e.g., "M", "ML") |
