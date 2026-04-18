# Fashion Rental Application — Development Guidelines

This is a fashion rental management application for a physical rental shop. A single owner/staff member uses it on an Android tablet via a PWA. Read `technical-architecture.md` and `fashion-rental-discovery.md` for full context before starting any feature work.

---

## ⚠️ Critical: Always Ask Before Git Actions

**Do not commit, push, or create a pull request without explicit user approval.** These actions are visible to the team and irreversible. Always ask the user first and wait for confirmation before proceeding.

Examples:
- ❌ "Creating a commit..." and then committing
- ✅ "I've made these changes. Ready to commit? Here's the message: `...`" → wait for user response
- ✅ "Should I push this branch to GitHub?" → wait for confirmation
- ✅ "I'm ready to create a PR with this title. Approve?" → wait for approval

---

## Project Structure

```
fashion-rental-application/
  features/                   ← story files; read before implementing any feature
  technical-architecture.md   ← ADRs, ERD, tech stack decisions
  fashion-rental-discovery.md ← business requirements, domain rules
  backend/                    ← Spring Boot app (Java 21, Gradle)
  frontend/                   ← React PWA (Vite, TypeScript, Ant Design)
```

Feature stories are indexed in `features/README.md`. Follow the suggested build order. Each story file contains the complete spec: entities, SQL, service code, API shape, frontend components, and test cases. Implement exactly what the story says — do not add unrequested features or refactor surrounding code.

---

## Getting Started

### Prerequisites
- Java 21 (Eclipse Temurin or Amazon Corretto)
- Gradle 8.x
- Node.js 20 LTS
- pnpm (frontend package manager)
- Docker & Docker Compose (for local PostgreSQL)

### Local Database Setup

```bash
# Start PostgreSQL on localhost:5433 (mapped from container 5432)
docker-compose up -d

# Verify connection
psql -h localhost -p 5433 -U fashion_user -d fashion_rental -c "SELECT 1"
# Password: (from docker-compose.yml)
```

The database is automatically initialized via Flyway migrations on first backend startup.

### Backend Setup

```bash
cd backend

# Run with dev profile (connects to localhost:5433)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Server runs on http://localhost:8080
```

### Frontend Setup

```bash
cd frontend

# Install dependencies
pnpm install

# Run dev server (http://localhost:5173)
pnpm dev

# Production build
pnpm build
```

---

## Build & Test Commands

### Backend

```bash
# Run application (dev profile connects to localhost:5433)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests com.fashionrental.inventory.service.AvailabilityServiceTest

# Run a single test method
./gradlew test --tests com.fashionrental.inventory.service.AvailabilityServiceTest.should_return_zero_availability_when_all_units_booked

# Linting & static analysis
./gradlew check

# Build JAR for production
./gradlew bootJar

# Run integration tests (uses Testcontainers for real PostgreSQL)
./gradlew integrationTest
```

### Frontend

```bash
# Development server
pnpm dev

# Run unit tests (Vitest)
pnpm test

# Run a single test file
pnpm test -- src/components/ItemBrowser.test.tsx

# Linting (ESLint)
pnpm lint

# Type checking (TypeScript)
pnpm type-check

# Build for production
pnpm build

# Preview production build locally
pnpm preview

# End-to-end tests (Playwright)
pnpm test:e2e
```

---

## Feature Story Workflow

All implementation guidance is in `features/` directory. **This is the single source of truth for feature requirements.**

### Story File Structure

Each story file (e.g., `features/01-inventory-management/US-101-browse-inventory.md`) contains:

1. **User Story** — acceptance criteria
2. **Backend Implementation** — entity classes, repository interfaces, service logic, controller endpoints with request/response shapes
3. **Frontend Implementation** — React components, hooks, API client calls, styling
4. **SQL Migrations** — Flyway migration file name and exact SQL
5. **Test Cases** — unit tests, integration tests with worked examples
6. **Key Decisions** — any ambiguities resolved during discovery

### Implementation Rules

- **Read the entire story file before starting.** It contains everything — entity definitions, API contracts, component props, test cases. Nothing is implied or left to interpretation.
- **Implement exactly what the story says.** Do not add unrequested features, UI polish, or "helpful" refactoring.
- **Follow the build order** in `features/README.md`. Some stories depend on prior ones (e.g., checkout depends on inventory + customer).
- **Test cases are specifications.** Implement them as written. If a test case fails, the story is incomplete.

### Build Order Summary

```
00-project-setup/                  ← Do first. Blocks everything.
  ├─ 01-spring-boot-scaffold.md
  └─ 02-react-pwa-scaffold.md

05-configuration/                  ← Do second (seeds late fee rules).
  └─ US-601-602-configuration.md

01-inventory-management/           ← In parallel with 02
  ├─ US-101, US-102, US-104, US-103, US-107 (P0)
  └─ US-105, US-106 (P1, later)

02-customer-management/            ← In parallel with 01
  ├─ US-201, US-202 (P0)
  └─ US-203, US-204 (P1, later)

03-checkout-and-receipt/           ← Depends on 01 + 02
  ├─ US-301-303-create-receipt.md
  └─ US-304-305-view-receipts.md

04-return-and-invoice/             ← Depends on 03 + 05
  └─ US-401-404-process-return.md

06-reporting/                      ← Read-only, do last
  └─ US-701-703-reports.md
```

---

## Environment Variables

### Backend (`backend/.env` or passed at startup)

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/fashion_rental
SPRING_DATASOURCE_USERNAME=fashion_user
SPRING_DATASOURCE_PASSWORD=<password_from_docker_compose>

# JWT
JWT_SECRET=<32+ char random secret key>
JWT_EXPIRATION_MS=86400000  # 24 hours

# Spring profile
SPRING_PROFILES_ACTIVE=dev  # dev or prod

# Cloudflare R2 (production only; ignored in dev)
R2_ACCOUNT_ID=<your-account-id>
R2_ACCESS_KEY_ID=<your-access-key>
R2_SECRET_ACCESS_KEY=<your-secret-key>
R2_BUCKET_NAME=fashion-rental
R2_PUBLIC_URL_BASE=https://pub-xxx.r2.dev

# Sentry (optional, for error tracking)
SENTRY_DSN=<your-sentry-dsn>
```

### Frontend (`frontend/.env.local`)

```bash
VITE_API_URL=http://localhost:8080  # Dev backend
VITE_API_URL=https://api.fashionrental.app  # Production
```

### Local Docker Compose PostgreSQL

The `docker-compose.yml` file sets:
```yaml
POSTGRES_USER: fashion_user
POSTGRES_PASSWORD: dev_password_change_in_prod
POSTGRES_DB: fashion_rental
```

For local development, these defaults are fine. For production, use a secrets manager (Railway/Render environment variables, AWS Secrets Manager, etc.).

---

## Testing Strategy

### Test Pyramid

```
End-to-End Tests (Playwright)
    ↑
    │ Selective. Cover critical flows only:
    │ - Full rental flow (checkout → receipt → return → invoice)
    │ - Error cases (double-booking prevention, invalid inputs)
    │
Integration Tests (JUnit 5 + Testcontainers)
    ↑
    │ Database boundaries. Real PostgreSQL via Testcontainers:
    │ - Repository queries and joins
    │ - Service layer with database state
    │ - Transactional behaviour
    │
Unit Tests (JUnit 5 + Mockito)
    ↑
    │ Fast, isolated, no I/O. 60-70% of test count:
    │ - Business logic (availability calculation, late fee tiers)
    │ - Validation rules (minimum rental duration, deposit calculations)
    │ - Error handling (invalid inputs, edge cases)
```

### Backend Testing

**Unit tests** — business logic with mocked dependencies:
```bash
# Example: test late fee calculation
./gradlew test --tests com.fashionrental.billing.service.LateFeeCalculationServiceTest
```

Conventions:
- Name tests as specifications: `should_calculate_late_fee_when_returned_3_hours_late`
- One clear action per test (Arrange–Act–Assert)
- Mock repositories, external services; test the service logic in isolation

**Integration tests** — queries, repositories, transactions with real database:
```bash
./gradlew integrationTest
```

Use `@DataJpaTest` + Testcontainers for PostgreSQL. Test:
- Availability queries (overlapping receipts, date range logic)
- Receipt creation with atomic availability recheck
- Snapshot pricing (rates captured at receipt time)

**Coverage expectations:**
- Target 80% with meaningful tests. Never write tests to hit a percentage.
- Avoid tautological tests (tests that mirror implementation exactly and add no value).
- Critical paths get 100% coverage: billing calculations, availability guards, transaction logic.
- UI plumbing (dumb components that pass props through) gets less focus.

### Frontend Testing

**Unit tests** (Vitest + React Testing Library):
```bash
pnpm test
```

Test individual components and hooks:
- Receipt total calculation (given items, dates, rates → totals are correct)
- Late fee tier UI logic (user selects tier → multiplier displays)
- Form validation (required fields, invalid dates → error messages)

Conventions:
- Test user interactions, not implementation details
- Use `screen.getByRole()` instead of `screen.getByTestId()`
- Mock API calls with `msw` (Mock Service Worker)

**End-to-end tests** (Playwright):
```bash
pnpm test:e2e
```

Selective coverage of critical workflows:
1. Browse inventory → check availability → register customer → create receipt
2. View active receipt → process return → generate invoice → verify calculations
3. Error case: attempt to double-book unavailable item → prevented by system

Conventions:
- Keep e2e tests lean — target 5-10 core flows
- Use test data fixtures (pre-seeded customers, items) for reliability
- Run in CI with real test database (Testcontainers)

### Running Tests Locally

```bash
# Backend: all tests
./gradlew test

# Backend: integration tests only
./gradlew integrationTest

# Backend: single test
./gradlew test --tests AvailabilityServiceTest

# Frontend: all tests
pnpm test

# Frontend: single component
pnpm test -- ItemBrowser

# Frontend: e2e
pnpm test:e2e

# Frontend: e2e single test
pnpm test:e2e -- checkout.spec.ts
```

---

## Project-Specific Conventions

### Monetary values
- Never use `float`, `double`, or `DECIMAL` for money. `INTEGER` in SQL, `int` in Java.

### Datetime handling
- All datetime fields: `TIMESTAMPTZ` in PostgreSQL, `OffsetDateTime` in Java.
- API accepts and returns ISO 8601 : `"2026-04-18T10:00:00"`.
- Timezone should be in IST

### Item packages
- A `PACKAGE` item has components defined in `package_components`.
- When creating a receipt for a package: add the package as one billed line item, then add each component as a zero-rate/zero-deposit line item to reserve its inventory.
- Availability check for a package = check the package itself AND each of its components.

### API response shape
Every endpoint returns `ApiResponse<T>`. Never return raw objects or Spring's default error page.

### Local development
- Build tool: **Gradle** (Kotlin DSL). Never use Maven.
- Run locally: `./gradlew bootRun --args='--spring.profiles.active=dev'`
- Local Postgres runs on host port **5433** (docker-compose maps `5433:5432`).

### Database migrations
- All schema changes go in `db/migration/` as Flyway versioned files (fine should be named by <datetime>__<about change>.sql).
- Hibernate `ddl-auto: validate` — a missing migration means the app refuses to start.

---

## Development Guidelines

### Core Philosophy

Write code that is **obvious**, **honest**, and **easy to delete**. Optimise for the next reader, not the next demo. When in doubt, prefer boring and correct over clever and fragile.

---

### Clean Code

- **Names are documentation.** A method called `processData` is a lie; `aggregateDeviceReadingsByMinute` is the truth. Names should reveal intent at the call site.
- **One level of abstraction per function.** Mixing high-level orchestration with low-level detail is the root of most unreadable code.
- **Small, focused units.** A function that does one thing is easy to name, test, and replace. If you need "and" in the name, split it.
- **No magic numbers or strings.** Extract them as named constants.
- **Avoid boolean parameters.** They silently encode invisible branching — introduce a type or two methods instead.
- **Return early.** Guard clauses over nested conditionals.
- **No dead code.** Delete it; version control remembers.
- **No commented-out code.** Ever.

---

### SOLID

**Single Responsibility** — a class/module has one reason to change. Violations show up as "and" in descriptions: "this service fetches data *and* sends alerts." Split it.

**Open/Closed** — extend behaviour through new types or strategy injection, not by modifying existing conditionals. When you find yourself adding another `if instanceOf`, reach for polymorphism.

**Liskov Substitution** — subtypes must honour the contract of the parent. If you override a method and change its semantics, you have broken the hierarchy — restructure instead.

**Interface Segregation** — expose only what callers need. Fat interfaces force unnecessary coupling. Prefer narrow, role-based interfaces.

**Dependency Inversion** — depend on abstractions, inject implementations. Don't instantiate collaborators inside a class; receive them. This is what makes testing possible.

---

### Error Handling

- **Fail fast, fail loud.** Never silently swallow exceptions. If you catch, either recover meaningfully or re-throw with context.
- **Typed errors over stringly-typed messages.** Custom exception types make error handling at call sites explicit and exhaustive.
- **Don't use exceptions for control flow.** Expected conditions (empty result, optional value) should be modelled with `Optional`, `Result`, or explicit return types.
- **Log at the right level.** Debug for diagnostic detail, warn for recoverable anomalies, error for failures needing attention. Avoid log spam — it buries real signals.

---

### ACID & Data Integrity

- **Every write path must be reasoned about transactionally.** Know which operations are atomic and which are not.
- **Idempotency is mandatory for retry-able operations.** Design consumers, handlers, and API endpoints to be safely re-executed.
- **Never assume a write succeeded.** Read-your-writes is not guaranteed across replicas or stores. Verify critical state explicitly.
- **Schema changes are data migrations.** Treat them with the same rigour — backward compatibility, rollback plan, staged rollout.

---

### Testing

Tests are the primary mechanism for proving correctness and enabling change. They are not optional, not afterthoughts, and not ceremonial.

**Standards**
- **Every non-trivial behaviour has a test.** If you can break the code without breaking a test, the test suite is incomplete.
- **Tests are code.** Apply the same naming, abstraction, and clarity standards. A flaky or unreadable test is technical debt.
- **Arrange–Act–Assert.** One clear setup, one action, one assertion per test. If you need multiple assertions, consider whether you are testing multiple behaviours.
- **Test behaviour, not implementation.** Tests that mirror internal structure break on every refactor. Test what the unit *does*, not how it does it.
- **Name tests as specifications.** `should_throw_when_offset_is_negative` > `testOffset`.

**Test Pyramid**
- **Unit tests** — fast, isolated, no I/O. Cover business logic, transformations, validations. The bulk of your suite.
- **Integration tests** — cover boundaries: database queries, repository behaviour. Use Testcontainers for real infrastructure in CI.
- **End-to-end tests** — selective. Cover critical flows only, not every code path.

**Coverage**
- Coverage is a signal, not a target. 80% with meaningful tests beats 100% with tautological ones.
- Never write a test to hit a number. Write it because a behaviour is important.

---

### Comments

No inline comments explaining *what* the code does — rewrite the code until it is clear. The only acceptable comments are:

- **Why** — a decision that is non-obvious and would be reversed by the next developer without context. Include a ticket reference if applicable.
- **Contract warnings** — pre/post conditions that callers must know and that cannot be encoded in the type system.

If you feel the urge to comment, ask whether the code can be made clearer instead.

---

### Documentation

- No doc comments for every method. Public APIs on shared libraries are the exception.
- **ADRs** for significant structural decisions — one short document per decision, recorded when the decision is made. Existing ADRs live in `technical-architecture.md`.
- `README.md` contains: how to run locally, how to run tests, environment variables required. Nothing else.

---

### Code Review

- Reviews are about correctness, clarity, and maintainability — not personal style preferences.
- Every comment is either a **blocker** (must change) or a **suggestion** (author's call). Be explicit.
- Approve when you'd be comfortable owning the code. Not before.
- If a review is taking more than an hour, the PR is too large.

---

### Git

- **Always ask before committing, pushing, or creating a PR.** These are irreversible actions visible to the team. Wait for explicit approval before proceeding.
- **Commits are logical units of change**, not save points. Each commit should be buildable and testable in isolation.
- **Commit messages state the why**, not the diff. The diff is already visible.
- Feature branches off `main`. Merge via PR only.
- No direct pushes to `main`.

---

### General

- **Premature optimisation is debt.** Make it correct first. Profile before optimising.
- **Duplication is better than the wrong abstraction.** Wait for the third occurrence before abstracting.
- **Prefer explicit over implicit.** Magic hurts the next person — and future you.
- **The boy scout rule.** Leave the code slightly cleaner than you found it. Not a full refactor; just a nudge.
