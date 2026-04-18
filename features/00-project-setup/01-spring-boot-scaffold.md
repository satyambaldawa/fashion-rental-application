# SETUP-01: Spring Boot Backend Scaffold

**Type:** Setup Task
**Priority:** P0 — Must complete before any feature work
**Depends On:** Nothing
**Blocks:** All backend feature stories

---

## Goal

Scaffold the Spring Boot backend application with all infrastructure wiring in place: package structure, database connection, Flyway, Spring Security skeleton, error handling, and a working health endpoint. A developer should be able to clone and run the backend locally after this task.

---

## Package Structure to Create

```
backend/
  src/
    main/
      java/
        com/fashionrental/
          FashionRentalApplication.java
          config/
            SecurityConfig.java
            JwtConfig.java
            CorsConfig.java
          common/
            exception/
              GlobalExceptionHandler.java
              ResourceNotFoundException.java
              ConflictException.java
              ValidationException.java
            response/
              ApiResponse.java         ← standard wrapper: { success, data, error }
            util/
              DateTimeUtil.java        ← rental day & overdue hour calculations
          inventory/                   ← empty package, filled in US-101+
          customer/                    ← empty package, filled in US-201+
          rental/                      ← empty package, filled in US-301+
          billing/                     ← empty package, filled in US-401+
          reporting/                   ← empty package, filled in US-701+
          configmgmt/                  ← empty package, filled in US-601+
      resources/
        application.yml
        application-dev.yml
        db/migration/
          V1__initial_schema.sql       ← all tables created here
    test/
      java/
        com/fashionrental/
          common/
            DateTimeUtilTest.java
  build.gradle
  settings.gradle
  Dockerfile
  docker-compose.yml
```

---

## build.gradle (Kotlin DSL)

```kotlin
plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
}

group = "com.fashionrental"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Data
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Image processing (for US-107)
    implementation("net.coobird:thumbnailator:0.4.20")

    // AWS SDK for Cloudflare R2 (for US-107) — see R2 note below
    implementation("software.amazon.awssdk:s3:2.25.60")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
```

**settings.gradle:**
```kotlin
rootProject.name = "fashion-rental"
```

---

## What is Cloudflare R2?

Cloudflare R2 is an S3-compatible object storage service. It works exactly like Amazon S3 (same API, same SDK), but with **zero egress fees** — you pay nothing to serve files to users. For this app we use R2 to store item photos.

- **Free tier:** 10 GB storage, 1 million Class-A operations/month — enough for the entire MVP lifetime
- **SDK:** The standard AWS S3 SDK (`software.amazon.awssdk:s3`) works out-of-the-box; just point the endpoint URL at your R2 account's endpoint instead of `s3.amazonaws.com`
- **Files per item:** Two files per upload — full resolution (1200px WebP) and thumbnail (300px WebP)
- **Public access:** R2 bucket is set to public read; images are served directly via a public URL

---

## application.yml

```yaml
spring:
  application:
    name: fashion-rental
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway manages schema; Hibernate only validates
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-hours: 24
  storage:
    r2:
      account-id: ${R2_ACCOUNT_ID}
      access-key-id: ${R2_ACCESS_KEY_ID}
      secret-access-key: ${R2_SECRET_ACCESS_KEY}
      bucket-name: ${R2_BUCKET_NAME}
      public-url-base: ${R2_PUBLIC_URL_BASE}
    image:
      max-upload-bytes: 15728640
      full-max-px: 1200
      thumb-max-px: 300
      max-photos-per-item: 8

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never

logging:
  level:
    com.fashionrental: INFO
    org.springframework.security: WARN
```

## application-dev.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/fashionrental
    username: postgres
    password: postgres
  jpa:
    show-sql: true

app:
  jwt:
    secret: dev-secret-key-min-32-chars-long-for-testing
  storage:
    r2:
      account-id: dev
      access-key-id: dev
      secret-access-key: dev
      bucket-name: dev
      public-url-base: http://localhost:9000
```

---

## V1__initial_schema.sql

Create all tables in one migration. Schema must match the ERD exactly.

> **Monetary columns convention:** All monetary values are stored as integers representing rupees × 100 (i.e., paise). For example, ₹150.00 is stored as `15000`. No `_paise` suffix is used — this is the implicit convention for every monetary column in this schema.

```sql
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- CUSTOMER
CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    address         TEXT,
    customer_type   VARCHAR(20) NOT NULL CHECK (customer_type IN ('STUDENT', 'PROFESSIONAL', 'MISC')),
    organization_name VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_customers_phone ON customers(phone);

-- ITEM
-- item_type: INDIVIDUAL = single costume/accessory; PACKAGE = a named bundle of multiple items
--   (e.g. "Freedom Fighter" = clothing + gun + ornaments + turban + beard & moustache)
-- For PACKAGE items, quantity and rate are set on the package row itself.
-- Component availability is checked individually when a package is rented.
CREATE TABLE items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(20) NOT NULL CHECK (category IN ('COSTUME','ACCESSORIES','PAGDI','DRESS','ORNAMENTS')),
    item_type       VARCHAR(10) NOT NULL DEFAULT 'INDIVIDUAL' CHECK (item_type IN ('INDIVIDUAL','PACKAGE')),
    size            VARCHAR(50),
    description     TEXT,
    rate            INTEGER NOT NULL CHECK (rate > 0),        -- rupees × 100
    deposit         INTEGER NOT NULL CHECK (deposit >= 0),    -- rupees × 100
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),  -- for PACKAGE: how many complete sets exist
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- PACKAGE_COMPONENTS
-- Defines which individual items (and how many of each) make up a PACKAGE item.
-- Only rows where the parent item has item_type='PACKAGE' should exist here.
-- When a package is rented:
--   1. Availability is checked for each component item individually.
--   2. All component items are reserved (via receipt_line_items) alongside the package itself.
CREATE TABLE package_components (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_item_id     UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    component_item_id   UUID NOT NULL REFERENCES items(id),
    quantity            INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    UNIQUE (package_item_id, component_item_id)
);
CREATE INDEX idx_package_components_package_id ON package_components(package_item_id);
CREATE INDEX idx_package_components_component_id ON package_components(component_item_id);

-- ITEM_PHOTO
CREATE TABLE item_photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id         UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    url             TEXT NOT NULL,
    thumbnail_url   TEXT NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_item_photos_item_id ON item_photos(item_id);

-- RECEIPT
CREATE TABLE receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number  VARCHAR(30) NOT NULL,
    customer_id     UUID NOT NULL REFERENCES customers(id),
    start_datetime  TIMESTAMPTZ NOT NULL,
    end_datetime    TIMESTAMPTZ NOT NULL,
    rental_days     INTEGER NOT NULL CHECK (rental_days >= 1),
    total_rent      INTEGER NOT NULL DEFAULT 0,     -- rupees × 100
    total_deposit   INTEGER NOT NULL DEFAULT 0,     -- rupees × 100
    grand_total     INTEGER NOT NULL DEFAULT 0,     -- rupees × 100
    status          VARCHAR(10) NOT NULL DEFAULT 'GIVEN' CHECK (status IN ('GIVEN','RETURNED')),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_receipts_receipt_number ON receipts(receipt_number);
CREATE INDEX idx_receipts_customer_id ON receipts(customer_id);
CREATE INDEX idx_receipts_status ON receipts(status);
CREATE INDEX idx_receipts_end_datetime ON receipts(end_datetime);

-- RECEIPT_LINE_ITEM
CREATE TABLE receipt_line_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id          UUID NOT NULL REFERENCES receipts(id) ON DELETE CASCADE,
    item_id             UUID NOT NULL REFERENCES items(id),
    quantity            INTEGER NOT NULL CHECK (quantity >= 1),
    rate_snapshot       INTEGER NOT NULL,    -- rupees × 100; copied from item at receipt creation
    deposit_snapshot    INTEGER NOT NULL,    -- rupees × 100; copied from item at receipt creation
    line_rent           INTEGER NOT NULL,    -- rupees × 100
    line_deposit        INTEGER NOT NULL     -- rupees × 100
);
CREATE INDEX idx_receipt_line_items_receipt_id ON receipt_line_items(receipt_id);
CREATE INDEX idx_receipt_line_items_item_id ON receipt_line_items(item_id);

-- INVOICE
CREATE TABLE invoices (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number              VARCHAR(30) NOT NULL,
    receipt_id                  UUID NOT NULL UNIQUE REFERENCES receipts(id),
    customer_id                 UUID NOT NULL REFERENCES customers(id),
    return_datetime             TIMESTAMPTZ NOT NULL,
    total_rent                  INTEGER NOT NULL,           -- rupees × 100
    total_deposit_collected     INTEGER NOT NULL,           -- rupees × 100
    total_late_fee              INTEGER NOT NULL DEFAULT 0, -- rupees × 100
    total_damage_cost           INTEGER NOT NULL DEFAULT 0, -- rupees × 100
    deposit_to_return           INTEGER NOT NULL DEFAULT 0, -- rupees × 100
    final_amount                INTEGER NOT NULL DEFAULT 0, -- rupees × 100
    transaction_type            VARCHAR(10) NOT NULL CHECK (transaction_type IN ('COLLECT','REFUND')),
    payment_method              VARCHAR(10) NOT NULL CHECK (payment_method IN ('CASH','UPI','OTHER')),
    damage_notes                TEXT,
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_return_datetime ON invoices(return_datetime);

-- INVOICE_LINE_ITEM
CREATE TABLE invoice_line_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id              UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    receipt_line_item_id    UUID NOT NULL REFERENCES receipt_line_items(id),
    item_id                 UUID NOT NULL REFERENCES items(id),
    quantity_returned       INTEGER NOT NULL,
    is_damaged              BOOLEAN NOT NULL DEFAULT FALSE,
    damage_percentage       NUMERIC(5,2),
    damage_cost             INTEGER NOT NULL DEFAULT 0,  -- rupees × 100
    late_fee                INTEGER NOT NULL DEFAULT 0   -- rupees × 100
);
CREATE INDEX idx_invoice_line_items_invoice_id ON invoice_line_items(invoice_id);

-- LATE_FEE_RULE
-- Sorted by duration_from_hours ASC when queried — no sort_order column needed.
CREATE TABLE late_fee_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    duration_from_hours INTEGER NOT NULL,
    duration_to_hours   INTEGER,                    -- NULL means infinity
    penalty_multiplier  NUMERIC(4,2) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default late fee rules (owner will adjust values before go-live)
INSERT INTO late_fee_rules (duration_from_hours, duration_to_hours, penalty_multiplier)
VALUES
    (0,   3,    0.50),   -- 0–3 hours late: 0.5x daily rate
    (3,   6,    0.75),   -- 3–6 hours late: 0.75x daily rate
    (6,   24,   1.00),   -- 6hrs–1 day late: 1x daily rate
    (24,  48,   1.50),   -- 1–2 days late: 1.5x daily rate
    (48,  NULL, 2.00);   -- 2+ days late: 2x daily rate
```

---

## Item Packages / Bundles

Some offerings are **packages** — a named bundle of multiple individual items rented together. For example, a "Freedom Fighter" costume includes: clothing, a toy gun, ornaments, a turban, and beard & moustache props.

### How it works

| Concept | Details |
|---------|---------|
| `item_type = 'PACKAGE'` | The package itself is an item with its own name, rate, deposit, and quantity (number of complete sets in stock) |
| `package_components` | Join table listing which INDIVIDUAL items (and how many) make up the package |
| Availability check | When checking if a package can be rented: check `package.quantity` minus active bookings of the package, **and** check each component item's individual availability |
| Receipt line items | The package is added as a single line item on the receipt (charged at package rate). Component items are also added as individual line items (quantity only, rate/deposit = 0) so inventory is correctly reserved |
| Inventory reservation | Component items' quantities are decremented by the availability query when the package is booked, preventing them from being double-booked as individual rentals |

### Example

```
Item: "Freedom Fighter Set"  — item_type=PACKAGE, rate=50000, deposit=100000, quantity=3
  package_components:
    → clothing (item_id=X, quantity=1)
    → toy gun  (item_id=Y, quantity=1)
    → ornaments (item_id=Z, quantity=1)
    → turban    (item_id=A, quantity=1)
    → beard & moustache (item_id=B, quantity=1)
```

When "Freedom Fighter Set ×1" is rented:
- Receipt has 6 line items: the package itself (billed) + 5 components (quantity reserved, ₹0 billed)
- Each component's availability count drops by 1 for that date range
- If clothing is separately rented out and only 2 remain, the package can only be booked ×2

---

## GlobalExceptionHandler.java

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred"));
    }
}
```

---

## ApiResponse.java

```java
public record ApiResponse<T>(boolean success, T data, String error) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
```

---

## DateTimeUtil.java

This is critical shared logic used everywhere in billing.

```java
@Component
public class DateTimeUtil {

    /**
     * Calculate rental days (exclusive, 24h increments).
     * Example: 10:00 AM Day 1 to 10:00 AM Day 2 = 1 day.
     */
    public int calculateRentalDays(OffsetDateTime start, OffsetDateTime end) {
        long seconds = ChronoUnit.SECONDS.between(start, end);
        int days = (int) (seconds / 86400);
        return Math.max(days, 1); // Minimum 1 day
    }

    /**
     * Calculate overdue hours for late fee calculation.
     * Returns 0 or negative if returned on time (no late fee applies).
     */
    public double calculateOverdueHours(OffsetDateTime endDatetime, OffsetDateTime returnDatetime) {
        long seconds = ChronoUnit.SECONDS.between(endDatetime, returnDatetime);
        return seconds / 3600.0;
    }
}
```

**Unit tests for DateTimeUtil:**
```
10:00 AM today → 10:00 AM tomorrow     = 1 day
10:00 AM today → 10:00 AM 3 days later = 3 days
10:00 AM today → 4:00 PM today (early) = 1 day (minimum enforced at service layer)
Return at 10:00 AM on due time          = 0 overdue hours (no late fee)
Return at 1:00 PM, due at 10:00 AM     = 3 overdue hours
Return at 10:00 AM next day, due today 10:00 AM = 24 overdue hours
```

---

## docker-compose.yml (local development)

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: fashionrental
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

> Port 5433 on the host maps to 5432 inside the container. This avoids conflicts if a system PostgreSQL instance is already running on 5432.

---

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
```

---

## Acceptance Criteria

- [ ] `./gradlew bootRun --args='--spring.profiles.active=dev'` starts the application without errors
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] Flyway runs V1__initial_schema.sql and creates all tables on startup (customers, items, package_components, item_photos, receipts, receipt_line_items, invoices, invoice_line_items, late_fee_rules)
- [ ] `DateTimeUtilTest` passes all rental day and overdue hour calculations
- [ ] `POST /api/auth/login` returns 401 for wrong credentials (Spring Security wired)
- [ ] All unhandled exceptions return `{ success: false, error: "..." }` JSON (not Spring's default error page)
