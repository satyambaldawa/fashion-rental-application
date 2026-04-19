# Showcase: Feature 00 — Project Setup

**Branch:** `feature/00-project-setup`
**Completed:** 2026-04-20
**Stories:** SETUP-01 (Spring Boot scaffold), SETUP-02 (React PWA scaffold)

---

## What Was Built

The complete foundational infrastructure for both backend and frontend. No business features yet — this establishes everything that all future features will be built on top of.

---

## What You Can Do Now

### 1. Log In

The application has a working login screen. A single shared account is used by all staff.

**Via the UI:**
1. Open http://localhost:5173
2. Enter username `admin` and password `admin`
3. You are redirected to `/inventory` (placeholder page)
4. Your session persists across page refreshes

**Via the API:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'
```
Response:
```json
{
  "success": true,
  "data": { "token": "<JWT token valid for 24 hours>" },
  "error": null
}
```

---

### 2. Navigate the App Shell

After logging in, the sidebar is visible with all top-level navigation links. Pages are placeholders at this stage — they will be filled in as each feature is built.

| Link | Route | Status |
|------|-------|--------|
| Inventory | `/inventory` | Placeholder |
| Customers | `/customers` | Placeholder |
| New Rental | `/checkout` | Placeholder |
| Active Rentals | `/receipts` | Placeholder |
| Reports | `/reports` | Placeholder |
| Settings | `/settings` | Placeholder |

---

### 3. Check Application Health

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

### 4. Browse the API with Swagger UI

All API endpoints are documented and testable at:
**http://localhost:8080/swagger-ui.html** (dev only)

**To test authenticated endpoints in Swagger:**
1. Call `POST /api/auth/login` → copy the token from the response
2. Click **Authorize** (top right) → paste the token → click **Authorize**
3. All `/api/**` endpoints are now accessible

---

### 5. Database Schema

All 9 tables are created and ready for data:

| Table | Purpose |
|-------|---------|
| `customers` | Customer profiles (name, phone, type) |
| `items` | Inventory catalog (costumes, accessories, etc.) |
| `package_components` | Components that make up a package item |
| `item_photos` | Photo URLs for items (stored on Cloudflare R2) |
| `receipts` | Rental agreements |
| `receipt_line_items` | Individual items on a receipt |
| `invoices` | Return settlements (late fees, damage, deposit refund) |
| `invoice_line_items` | Per-item damage and late fee breakdown |
| `late_fee_rules` | Configurable overdue fee tiers (seeded with defaults) |

**Default late fee rules (seeded):**

| Overdue Duration | Penalty |
|-----------------|---------|
| 0 – 3 hours | 0.5× daily rate |
| 3 – 6 hours | 0.75× daily rate |
| 6 hours – 1 day | 1× daily rate |
| 1 – 2 days | 1.5× daily rate |
| 2+ days | 2× daily rate |

---

## How to Run Locally

**Prerequisites:** Java 21, Gradle, Node.js 20, pnpm, PostgreSQL running on port 5433

```bash
# 1. Start the database
podman compose up -d

# 2. Start the backend (terminal 1)
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. Start the frontend (terminal 2)
cd frontend
pnpm dev
```

Open **http://localhost:5173** in your browser.

---

## API Conventions Established

All future endpoints follow these conventions set in this feature:

- **Response envelope:** Every endpoint returns `ApiResponse<T>`
  ```json
  { "success": true, "data": { ... }, "error": null }
  { "success": false, "data": null, "error": "Error message" }
  ```
- **Auth:** Pass `Authorization: Bearer <token>` header on all `/api/**` requests
- **Monetary amounts:** Whole rupees as integers. ₹500 → `500`
- **Datetimes:** ISO 8601 with IST offset. e.g. `"2026-04-20T10:00:00+05:30"`
- **Error HTTP codes:** `400` validation, `401` unauthenticated, `403` forbidden, `404` not found, `409` conflict, `500` unexpected

---

## What's Next

Next feature to implement: **05-configuration** (late fee rules UI) followed by **01-inventory-management**.

See `features/README.md` for the full build order and story status.
