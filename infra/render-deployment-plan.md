# Production Deployment Plan — Render + Cloudflare

**Status**: Draft  
**Target stack**: Render (Web Service + PostgreSQL + Static Site) + Cloudflare (DNS + CDN) + Cloudflare R2  
**Estimated monthly cost**: ~$14.85 USD  
**Region**: Singapore (`ap-southeast-1`) — lowest latency for IST timezone  

---

## Production Architecture

```mermaid
graph TB
    subgraph User["User (Android Tablet)"]
        T[PWA — yourshop.in]
    end

    subgraph CF["Cloudflare (Free)"]
        CFDNS["DNS + CDN Proxy\nyourshop.in\napi.yourshop.in"]
    end

    subgraph Render["Render (Singapore)"]
        subgraph Public["Public Services"]
            FE["Static Site\nReact PWA\nyourshop.in\n[Free]"]
            BE["Web Service\nSpring Boot :8080\napi.yourshop.in\n[$7/mo]"]
        end

        subgraph Private["Private Network (Render internal)"]
            DB["PostgreSQL\n1GB storage\nDaily backups\n[$7/mo]"]
        end

        BE -->|"Internal connection string\n(never touches internet)"| DB
    end

    subgraph External["External Services"]
        R2["Cloudflare R2\nImage storage\nUp to 10GB free"]
        GH["GitHub\nmain branch"]
        GHA["GitHub Actions\nCI — test gate"]
        Sentry["Sentry\nError tracking\n(optional, free tier)"]
    end

    T -->|"HTTPS"| CFDNS
    CFDNS -->|"CDN edge cache\nserves static files"| FE
    CFDNS -->|"Proxied — origin IP hidden"| BE
    FE -->|"API calls\nBearer JWT"| CFDNS
    BE -->|"S3-compatible API\nHMAC credentials"| R2
    BE -->|"Error events"| Sentry
    GH -->|"Push to main"| GHA
    GHA -->|"Tests pass → webhook"| BE
    GHA -->|"Tests pass → webhook"| FE
```

---

## Request Flow (End-to-End)

```mermaid
sequenceDiagram
    participant T as Android Tablet
    participant CF as Cloudflare CDN
    participant FE as Render Static Site
    participant BE as Render Spring Boot
    participant DB as Render PostgreSQL
    participant R2 as Cloudflare R2

    Note over T,FE: App Load
    T->>CF: GET https://yourshop.in
    CF->>FE: Forward (or serve from edge cache)
    FE-->>CF: React PWA static bundle
    CF-->>T: HTTPS response (gzip, cached)

    Note over T,BE: Authentication
    T->>CF: POST /api/auth/login
    CF->>BE: Forward (proxied)
    BE-->>T: 200 { token: "eyJ..." }

    Note over T,DB: Checkout Flow
    T->>CF: POST /api/receipts (Bearer token)
    CF->>BE: Forward
    BE->>DB: BEGIN TRANSACTION
    BE->>DB: SELECT availability (lock rows)
    BE->>DB: INSERT receipt + line items
    BE->>DB: COMMIT
    DB-->>BE: Success
    BE-->>T: 201 { receipt }

    Note over T,R2: Photo Upload
    T->>CF: POST /api/items/{id}/photos (multipart)
    CF->>BE: Forward
    BE->>R2: PUT object (HMAC signed)
    R2-->>BE: 200 ETag
    BE->>DB: UPDATE item photo_urls
    BE-->>T: 200 { photoUrl }
```

---

## Deployment Pipeline

```mermaid
flowchart LR
    Dev["Developer\npushes code"] -->|"git push origin main"| GH["GitHub\nmain branch"]
    GH -->|"Triggers"| GHA["GitHub Actions CI"]

    subgraph CI["GitHub Actions CI"]
        BT["Backend tests\n./gradlew test"] 
        FT["Frontend tests\npnpm type-check\npnpm test"]
    end

    GHA --> BT
    GHA --> FT

    BT -->|"Pass"| RD["Render Deploy Hook"]
    FT -->|"Pass"| RD

    subgraph RenderDeploy["Render Auto-Deploy"]
        RBBE["Backend build\n./gradlew bootJar -x test\nFlyway migrations on startup"]
        RBFE["Frontend build\npnpm build → dist/\nDeploy to CDN edge"]
    end

    RD --> RBBE
    RD --> RBFE

    RBBE -->|"Zero-downtime swap"| LIVE["Live in ~5 min"]
    RBFE -->|"Instant CDN update"| LIVE
```

---

## Task Breakdown

---

### TASK-1: Fix CORS Configuration

**File**: `backend/src/main/java/com/fashionrental/config/CorsConfig.java`  
**File**: `backend/src/main/resources/application.yml`

**Problem**: CORS currently allows all origins (`*`). In production this must be locked to the frontend domain only.

**Current code** (`CorsConfig.java:18`):
```java
config.setAllowedOriginPatterns(List.of("*"));
```

**Change 1 — `CorsConfig.java`**: Read allowed origins from an injected property.
```java
package com.fashionrental.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
```

**Change 2 — `application.yml`**: Add CORS config block driven by env var.
```yaml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS}
  # ... existing jwt, auth, storage config
```

**Change 3 — `application-dev.yml`**: Add dev default so local dev keeps working.
```yaml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173}
```

**Render env var to set**:
```
ALLOWED_ORIGINS=https://yourshop.in
```

**Test**: After deploying, open browser devtools on a different origin and verify preflight returns 403. From `yourshop.in`, verify it returns 200.

---

### TASK-2: Fix Frontend API Base URL

**File**: `frontend/src/api/client.ts`

**Problem**: `baseURL: '/api'` relies on Vite's dev proxy. In production the frontend is served from Render's CDN — there is no proxy. API calls must point to the backend's public URL.

**Current code** (`client.ts:5`):
```ts
baseURL: '/api',
```

**Change — `client.ts`**:
```ts
const client = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL ?? ''}/api`,
  headers: { 'Content-Type': 'application/json' }
})
```

**How this works**:
- In dev: `VITE_API_URL` is undefined → `baseURL` becomes `/api` → Vite proxy handles it. No change to local dev workflow.
- In prod: Render injects `VITE_API_URL=https://api.yourshop.in` → `baseURL` becomes `https://api.yourshop.in/api`. Direct HTTPS call to the backend.

**Render env var to set** (on the Static Site service):
```
VITE_API_URL=https://api.yourshop.in
```

**Test**: Open network tab in browser on production. All `/api/*` requests must go to `https://api.yourshop.in/api/*`, not `yourshop.in/api/*`.

---

### TASK-3: Add Render Blueprint (`render.yaml`)

**File**: `render.yaml` (root of repository)

This is Render's infrastructure-as-code file. It lets you recreate the entire stack by connecting the repo to Render once — no clicking through the UI to configure every field.

```yaml
# render.yaml
# Render Blueprint — defines all services declaratively.
# Sensitive values (marked sync: false) must be pasted manually in the Render dashboard.
# See infra/render-deployment-plan.md for the full list of required env vars.

services:

  - type: web
    name: fashion-rental-backend
    runtime: java
    region: singapore
    plan: starter
    buildCommand: cd backend && ./gradlew bootJar -x test
    startCommand: java -Xms256m -Xmx400m -jar backend/build/libs/fashion-rental-*.jar
    healthCheckPath: /actuator/health
    autoDeploy: false   # CI must pass first; deploy hook triggered by GitHub Actions
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: DATABASE_URL
        sync: false
      - key: DATABASE_USERNAME
        sync: false
      - key: DATABASE_PASSWORD
        sync: false
      - key: JWT_SECRET
        generateValue: true
      - key: APP_USERNAME
        sync: false
      - key: APP_PASSWORD
        sync: false
      - key: ALLOWED_ORIGINS
        sync: false
      - key: R2_ACCOUNT_ID
        sync: false
      - key: R2_ACCESS_KEY_ID
        sync: false
      - key: R2_SECRET_ACCESS_KEY
        sync: false
      - key: R2_BUCKET_NAME
        sync: false
      - key: R2_PUBLIC_URL_BASE
        sync: false

  - type: web
    name: fashion-rental-frontend
    runtime: static
    region: singapore
    plan: free
    buildCommand: cd frontend && pnpm install && pnpm build
    staticPublishPath: frontend/dist
    autoDeploy: false
    headers:
      - path: /*
        name: Cache-Control
        value: no-cache            # HTML — always revalidate
      - path: /assets/*
        name: Cache-Control
        value: public, max-age=31536000, immutable   # Hashed chunks — cache forever
    routes:
      - type: rewrite
        source: /*
        destination: /index.html   # SPA fallback for React Router
    envVars:
      - key: VITE_API_URL
        sync: false

databases:
  - name: fashion-rental-db
    region: singapore
    plan: starter
    databaseName: fashion_rental
    user: fashion_user
```

**Notes on JVM flags**: `-Xms256m -Xmx400m` keeps the heap under Render Starter's 512MB RAM limit. Without this, the JVM will attempt to use more memory and be OOM-killed.

---

### TASK-4: Add GitHub Actions CI

**File**: `.github/workflows/ci.yml`

Blocks Render deploys if tests fail. The `autoDeploy: false` in `render.yaml` means Render only deploys when the GitHub Actions workflow explicitly triggers the deploy hook (configured in the workflow).

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend:
    name: Backend tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - name: Run tests
        run: cd backend && ./gradlew test

  frontend:
    name: Frontend tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'

      - uses: pnpm/action-setup@v3
        with:
          version: 9

      - name: Install dependencies
        run: cd frontend && pnpm install --frozen-lockfile

      - name: Type check
        run: cd frontend && pnpm type-check

      - name: Unit tests
        run: cd frontend && pnpm test --run

  deploy:
    name: Deploy to Render
    runs-on: ubuntu-latest
    needs: [backend, frontend]   # only runs if both test jobs pass
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    steps:
      - name: Trigger backend deploy
        run: |
          curl -s -X POST "${{ secrets.RENDER_BACKEND_DEPLOY_HOOK }}"

      - name: Trigger frontend deploy
        run: |
          curl -s -X POST "${{ secrets.RENDER_FRONTEND_DEPLOY_HOOK }}"
```

**GitHub Secrets to add** (in repo Settings → Secrets → Actions):
```
RENDER_BACKEND_DEPLOY_HOOK   ← copied from Render dashboard → Backend service → Settings → Deploy Hook URL
RENDER_FRONTEND_DEPLOY_HOOK  ← copied from Render dashboard → Frontend service → Settings → Deploy Hook URL
```

---

### TASK-5: Render Service Setup (Manual — Dashboard)

Do this after TASK-1 through TASK-4 are merged to `main`.

#### 5a. Create PostgreSQL

1. Render Dashboard → **New** → **PostgreSQL**
2. Settings:
   - Name: `fashion-rental-db`
   - Region: **Singapore**
   - Plan: **Starter ($7/mo)**
   - Database name: `fashion_rental`
   - User: `fashion_user`
3. After creation, copy the **Internal Database URL** (format: `postgres://fashion_user:xxx@dpg-xxx.singapore-postgres.render.com/fashion_rental`)
4. Also note the individual **Host**, **Username**, **Password** fields — you will paste these as separate env vars on the backend service

#### 5b. Create Backend Web Service

1. Render Dashboard → **New** → **Web Service**
2. Connect GitHub repo → select `fashion-rental-application`
3. Settings:
   - Name: `fashion-rental-backend`
   - Region: **Singapore**
   - Branch: `main`
   - Runtime: **Java**
   - Build command: `cd backend && ./gradlew bootJar -x test`
   - Start command: `java -Xms256m -Xmx400m -jar backend/build/libs/fashion-rental-*.jar`
   - Plan: **Starter ($7/mo)**
   - Auto-deploy: **Off** (GitHub Actions will trigger via deploy hook)
4. Environment variables (paste all of these):

| Key | Value | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Disables Swagger, enables prod settings |
| `DATABASE_URL` | `jdbc:postgresql://dpg-xxx...` | Convert Render's postgres:// → jdbc:postgresql:// |
| `DATABASE_USERNAME` | `fashion_user` | From Render DB dashboard |
| `DATABASE_PASSWORD` | `<from Render DB dashboard>` | From Render DB dashboard |
| `JWT_SECRET` | `<generate: openssl rand -base64 48>` | Min 32 chars |
| `APP_USERNAME` | `admin` (or choose) | Login username for the app |
| `APP_PASSWORD` | `<strong password>` | Login password for the app |
| `ALLOWED_ORIGINS` | `https://yourshop.in` | Update after custom domain is set |
| `R2_ACCOUNT_ID` | `<from Cloudflare>` | R2 → Manage R2 API Tokens |
| `R2_ACCESS_KEY_ID` | `<from Cloudflare>` | R2 API token |
| `R2_SECRET_ACCESS_KEY` | `<from Cloudflare>` | R2 API token |
| `R2_BUCKET_NAME` | `fashion-rental` | Create this bucket in R2 first |
| `R2_PUBLIC_URL_BASE` | `https://pub-xxx.r2.dev` | Bucket's public URL |

> **DATABASE_URL note**: Render provides the URL as `postgres://user:pass@host/db`. Spring Boot needs `jdbc:postgresql://user:pass@host/db`. Replace the scheme prefix when pasting.

5. After service is created → **Settings** → **Deploy Hook** → copy the URL → add as `RENDER_BACKEND_DEPLOY_HOOK` in GitHub Secrets

#### 5c. Create Frontend Static Site

1. Render Dashboard → **New** → **Static Site**
2. Connect same GitHub repo
3. Settings:
   - Name: `fashion-rental-frontend`
   - Region: **Singapore**
   - Branch: `main`
   - Build command: `cd frontend && pnpm install && pnpm build`
   - Publish directory: `frontend/dist`
   - Auto-deploy: **Off**
4. Environment variables:

| Key | Value |
|---|---|
| `VITE_API_URL` | `https://api.yourshop.in` (update after custom domain) |

5. Rewrite rule: `/* → /index.html` (Status: 200) — this is the SPA fallback
6. After creation → copy deploy hook URL → add as `RENDER_FRONTEND_DEPLOY_HOOK` in GitHub Secrets

---

### TASK-6: Domain Registration + Cloudflare Setup

#### 6a. Register domain

1. Go to [Namecheap](https://namecheap.com) (or any registrar)
2. Search `yourshopname.in` — `.in` domains cost ~₹800-900/year
3. Register it
4. In Namecheap → **Nameservers** → change to **Custom DNS**

#### 6b. Add to Cloudflare

1. [Cloudflare Dashboard](https://dash.cloudflare.com) → **Add a Site** → enter your domain
2. Select **Free plan**
3. Cloudflare will show you two nameserver hostnames (e.g. `aisha.ns.cloudflare.com`)
4. Paste those into Namecheap's Custom DNS fields
5. Nameserver propagation: typically 15-30 minutes

#### 6c. Create DNS records in Cloudflare

Once Cloudflare shows the domain as **Active**:

| Type | Name | Target | Proxy |
|---|---|---|---|
| CNAME | `@` (root) | `fashion-rental-frontend.onrender.com` | Proxied (orange cloud) |
| CNAME | `api` | `fashion-rental-backend.onrender.com` | Proxied (orange cloud) |

Proxied = Cloudflare hides your Render origin IP. All traffic goes through Cloudflare's edge.

#### 6d. Add custom domains in Render

1. Render → Backend service → **Settings** → **Custom Domains** → add `api.yourshop.in`
2. Render → Frontend service → **Settings** → **Custom Domains** → add `yourshop.in`
3. Render will verify domain ownership and auto-provision Let's Encrypt TLS certificates (~2-5 minutes)

#### 6e. Update env vars after domain is live

- Backend `ALLOWED_ORIGINS`: change from the temporary `.onrender.com` URL to `https://yourshop.in`
- Frontend `VITE_API_URL`: change from the temporary `.onrender.com` URL to `https://api.yourshop.in`
- Trigger a manual redeploy of both services after updating

---

### TASK-7: Cloudflare R2 Bucket Setup

1. Cloudflare Dashboard → **R2** → **Create bucket**
2. Name: `fashion-rental`
3. Location: **APAC** (closest to Singapore)
4. After creation → **Settings** → **Public Access** → enable (needed for serving item photos publicly)
5. Note the **Public Bucket URL** (format: `https://pub-xxx.r2.dev`) → this is `R2_PUBLIC_URL_BASE`
6. **R2** → **Manage R2 API Tokens** → **Create API Token**
   - Permissions: **Object Read & Write** on bucket `fashion-rental`
   - Copy `Account ID`, `Access Key ID`, `Secret Access Key`
   - Paste all three into Render backend env vars (TASK-5b)

---

### TASK-8: Go-Live Validation Checklist

Run through this in order after all DNS propagates and both Render services show green.

```
Infrastructure
[ ] https://yourshop.in loads without certificate warning
[ ] https://api.yourshop.in/actuator/health returns {"status":"UP"}
[ ] https://yourshop.in/swagger-ui.html returns 404 (Swagger disabled in prod)
[ ] https://api.yourshop.in (no path) returns 404, not a Spring error page

Authentication
[ ] Login with APP_USERNAME / APP_PASSWORD succeeds, returns JWT
[ ] Invalid credentials return 401
[ ] Accessing a protected endpoint without token returns 401

Core Flows
[ ] Browse inventory — items load
[ ] Create a new item with a photo — photo URL uses R2 domain (pub-xxx.r2.dev)
[ ] Check availability for an item
[ ] Create a customer
[ ] Create a checkout receipt
[ ] Process a return, generate invoice

PWA Installation
[ ] Open https://yourshop.in in Chrome on Android tablet
[ ] Browser shows "Add to Home Screen" prompt
[ ] Install — icon appears on home screen
[ ] Launch from home screen — runs fullscreen, no browser chrome

Deployment Pipeline
[ ] Push a trivial change to main
[ ] GitHub Actions runs both test jobs
[ ] On success, both Render deploy hooks are triggered
[ ] New version live within ~5 minutes for backend, ~1 minute for frontend
[ ] Push a change that breaks a test — verify deploy hook is NOT triggered
```

---

## Environment Variable Reference

Complete list of all env vars required in production, in one place.

### Backend (Render Web Service)

| Variable | Example Value | Where to get it |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Hardcode |
| `DATABASE_URL` | `jdbc:postgresql://dpg-xxx.singapore-postgres.render.com/fashion_rental` | Render DB dashboard → Internal URL (replace `postgres://` with `jdbc:postgresql://`) |
| `DATABASE_USERNAME` | `fashion_user` | Render DB dashboard |
| `DATABASE_PASSWORD` | `abc123xyz...` | Render DB dashboard |
| `JWT_SECRET` | 48-char random base64 string | `openssl rand -base64 48` |
| `APP_USERNAME` | `admin` | Choose |
| `APP_PASSWORD` | Strong password | Choose |
| `ALLOWED_ORIGINS` | `https://yourshop.in` | Your domain |
| `R2_ACCOUNT_ID` | `abc123...` | Cloudflare → R2 → Account ID |
| `R2_ACCESS_KEY_ID` | `xyz...` | Cloudflare → R2 API Token |
| `R2_SECRET_ACCESS_KEY` | `secret...` | Cloudflare → R2 API Token |
| `R2_BUCKET_NAME` | `fashion-rental` | Bucket name you created |
| `R2_PUBLIC_URL_BASE` | `https://pub-xxx.r2.dev` | R2 bucket → Public URL |

### Frontend (Render Static Site)

| Variable | Example Value | Notes |
|---|---|---|
| `VITE_API_URL` | `https://api.yourshop.in` | No trailing slash |

### GitHub Actions Secrets

| Secret | Where to get it |
|---|---|
| `RENDER_BACKEND_DEPLOY_HOOK` | Render → Backend service → Settings → Deploy Hook |
| `RENDER_FRONTEND_DEPLOY_HOOK` | Render → Frontend service → Settings → Deploy Hook |

---

## Cost Summary

| Service | Plan | Monthly cost |
|---|---|---|
| Render Web Service (Spring Boot) | Starter — 512MB RAM, 0.5 CPU | $7.00 |
| Render PostgreSQL | Starter — 1GB storage, daily backups | $7.00 |
| Render Static Site (React PWA) | Free | $0.00 |
| Cloudflare DNS + CDN | Free | $0.00 |
| Cloudflare R2 (< 10GB storage, < 1M requests) | Free | $0.00 |
| Domain (`.in`, amortized monthly) | — | ~$0.85 |
| **Total** | | **~$14.85/month** |
