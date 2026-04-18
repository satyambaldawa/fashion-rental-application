# Fashion Rental Application

A point-of-sale and operations management platform for a physical fashion rental shop. Digitizes inventory management, customer registration, rental billing, return processing, and financial reporting.

**Stack:** Java 21 + Spring Boot 3.x (backend) | React 18+ (frontend PWA) | PostgreSQL 16

---

## Local Development

### Prerequisites

- Java 21 (Eclipse Temurin or Amazon Corretto)
- Gradle 8.x
- Node.js 20 LTS
- pnpm
- Docker & Docker Compose

### Quick Start

1. **Start the database:**
   ```bash
   docker-compose up -d
   ```

2. **Run the backend** (http://localhost:8080):
   ```bash
   cd backend
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

3. **Run the frontend** (http://localhost:5173):
   ```bash
   cd frontend
   pnpm install
   pnpm dev
   ```

The database is auto-initialized via Flyway migrations on backend startup.

---

## Running Tests

**Backend:**
```bash
cd backend

# All tests
./gradlew test

# Single test class
./gradlew test --tests AvailabilityServiceTest

# Single test method
./gradlew test --tests AvailabilityServiceTest.should_return_zero_availability_when_all_units_booked

# Integration tests (requires Testcontainers)
./gradlew integrationTest
```

**Frontend:**
```bash
cd frontend

# Unit tests
pnpm test

# E2E tests (Playwright)
pnpm test:e2e

# Type checking
pnpm type-check
```

---

## Environment Variables

### Backend

```bash
# Database (defaults match docker-compose)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/fashion_rental
SPRING_DATASOURCE_USERNAME=fashion_user
SPRING_DATASOURCE_PASSWORD=dev_password_change_in_prod

# JWT (use a 32+ character random secret in production)
JWT_SECRET=your_secret_key_here
JWT_EXPIRATION_MS=86400000

# Spring profile
SPRING_PROFILES_ACTIVE=dev
```

### Frontend

```bash
# API endpoint
VITE_API_URL=http://localhost:8080
```

---

## Project Structure

```
fashion-rental-application/
  backend/                    ← Spring Boot app (Java 21, Gradle)
    src/main/java/
      com/fashionrental/
        inventory/            ← Inventory module
        customer/             ← Customer module
        rental/               ← Receipt & checkout
        billing/              ← Late fees & invoices
        reporting/            ← Reports
        configmgmt/           ← Late fee rules config
        common/               ← Shared utilities
    src/main/resources/
      db/migration/           ← Flyway SQL migrations
  frontend/                   ← React PWA (Vite, TypeScript)
    src/
      components/
      pages/
      services/               ← API client
      hooks/
  features/                   ← Implementation-ready story files
    00-project-setup/
    01-inventory-management/
    02-customer-management/
    03-checkout-and-receipt/
    04-return-and-invoice/
    05-configuration/
    06-reporting/
  CLAUDE.md                   ← Development guidelines for Claude Code
  docker-compose.yml          ← Local PostgreSQL
```

---

## Architecture Overview

**Modular Monolith** — Clean module boundaries (inventory, customer, rental, billing, reporting) with clear separation of concerns. Modules can be extracted into services later if the business grows.

**Key Technical Decisions:**

| Decision | Choice | Reason |
|----------|--------|--------|
| Rental periods | Datetime-based (not date-only) | Late fee calculation requires hour-level granularity |
| Availability | Query-based (not cached) | Always accurate; recalculated on every checkout |
| PO (Purchase Order) | Not persisted | Stateless preview endpoint; checkout is atomic with DB transaction |
| Customer PK | UUID (not phone) | Phone is unique but mutable; UUID ensures stable foreign keys |
| Receipt & Invoice | Two separate entities | Different shapes and purposes; cleaner reporting and transactions |
| Image storage | Cloudflare R2 | Free tier covers full shop needs; zero egress fees; S3-compatible API |

See `technical-architecture.md` and `fashion-rental-discovery.md` for full context.

---

## Development Guidelines

- **Read `CLAUDE.md`** for philosophy (clean code, SOLID, error handling, testing standards) and practical guidance (build commands, test strategies, feature story workflow).
- **Feature stories are complete specs** in `features/` directory. Implement exactly what each story says; don't add unrequested features.
- **Follow the build order** in `features/README.md` — some stories depend on prior ones.
- **No direct pushes to `main`** — feature branches off `main`, merge via PR only.

---

## Deployment

See `technical-architecture.md` § 6 (Infrastructure and Deployment) for hosting options (Railway, Render, DigitalOcean) and deployment strategy.

---

## Documentation

- **`CLAUDE.md`** — Development guidelines, build commands, testing strategy, feature story workflow
- **`technical-architecture.md`** — Architecture decisions (ADRs), ERD, tech stack, system design, deployment architecture
- **`fashion-rental-discovery.md`** — Business requirements, user stories, open questions, assumptions
- **`features/`** — Implementation-ready feature specifications with entities, APIs, components, and tests
