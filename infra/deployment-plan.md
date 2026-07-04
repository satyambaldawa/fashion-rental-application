# Production Deployment Plan — GCP + Neon + Cloudflare

**Status**: Draft
**Target stack**: GCP Compute Engine (Docker) + Neon (PostgreSQL) + Cloudflare Pages (frontend) + Cloudflare R2 (photos) + Cloudflare DNS
**Estimated monthly cost**: ~₹75/month (domain only)
**Region**: GCP us-central1 (free tier) — ~180-220ms latency from India, acceptable for a single-user business tool

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Android Tablet (PWA)                                       │
└──────────────┬──────────────────────────┬───────────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────┐    ┌─────────────────────────────┐
│  Cloudflare (free)   │    │  Cloudflare R2 (free)       │
│  yourshop.in         │    │  Item photos                │
│  api.yourshop.in     │    │  No egress fees when served │
│  DNS + CDN proxy     │    └─────────────────────────────┘
└──────┬───────────────┘                  ▲
       │                                  │ uploads via backend
       ├──────────────────────────────────┐
       │                                  │
       ▼                                  ▼
┌─────────────────────────┐       ┌───────────────────────────┐
│  Cloudflare Pages       │       │  GCP e2-micro (Docker)    │
│  React PWA (free)       │       │  us-central1 (free)       │
│  yourshop.in            │       │  Spring Boot container    │
│  Global CDN edge        │       │  api.yourshop.in          │
│  Deploy: wrangler CLI   │       │  Image: ghcr.io           │
└─────────────────────────┘       └──────────────┬────────────┘
                                                 │
                                                 ▼
                                  ┌───────────────────────────┐
                                  │  Neon (free tier)         │
                                  │  Managed PostgreSQL        │
                                  │  0.5GB, auto-backups      │
                                  │  Region: AWS us-east-1    │
                                  │  SSL enforced             │
                                  └───────────────────────────┘
```

---

## Request Flow

```
Photo Upload (current — backend as middleman):
  Tablet → Cloudflare → GCP: POST /api/items/{id}/photos (multipart)
  GCP → R2:                  PUT object (counts against 1GB free egress — negligible)
  GCP → Tablet:              200 { photoUrl }

API Call:
  Tablet → Cloudflare CDN → GCP e2-micro (Docker) → Neon PostgreSQL (US)

Frontend Load:
  Tablet → Cloudflare CDN edge → Cloudflare Pages (static bundle, cached globally)

Image Serve:
  Tablet → Cloudflare R2 public URL (no GCP involved, R2 has no egress fees)
```

> **Tech Debt**: Photo upload currently routes through GCP backend, consuming GCP egress.
> Migrate to presigned R2 URLs so the tablet uploads directly to R2. See Tech Debt section.

---

## Deployment Pipeline

CI and CD are separated. CI runs automatically on PRs and merges. CD is manual trigger only.

```
Pull Request / Merge to main
        │
        ▼
GitHub Actions CI (automatic)
        ├── backend-test:  ./gradlew test
        └── frontend-test: pnpm type-check && pnpm test --run
        └── (no deploy — CI only)

Manual Trigger (workflow_dispatch)
        │
        ▼
GitHub Actions CD
        ├── deploy-backend:
        │     Build Docker image → push to ghcr.io → SSH to GCP VM → docker pull → restart
        └── deploy-frontend:
              pnpm build → wrangler pages deploy
```

Infrastructure provisioning is also a GitHub Actions workflow (manual trigger), used once to create the GCP VM.

---

## Cost Breakdown

| Component | Service | Plan | Cost |
|-----------|---------|------|------|
| Backend (Spring Boot) | GCP e2-micro (Docker) | Always Free (us-central1) | ₹0 |
| Database (PostgreSQL) | Neon | Free tier (0.5GB, us-east-1) | ₹0 |
| Frontend (React PWA) | Cloudflare Pages | Free | ₹0 |
| Photos | Cloudflare R2 | Free (10GB, 1M req/month) | ₹0 |
| DNS + CDN | Cloudflare | Free | ₹0 |
| Container Registry | GitHub Container Registry | Free (public repo) | ₹0 |
| Domain | `.in` TLD | ~₹900/year | ~₹75/month |
| **Total** | | | **~₹75/month** |

GCP egress: DB queries to Neon + photo uploads to R2 + pg_dump backups ≈ 200-400MB/month. Well within 1GB free limit.

---

## Prerequisites (One-Time Manual Setup)

These steps must be done manually before any GitHub Actions workflows can run.

### P1. Create GCP Project + Service Account

1. Go to [console.cloud.google.com](https://console.cloud.google.com) → **Create Project**
   - Name: `fashion-rental`
   - Note the **Project ID** (e.g. `fashion-rental-123456`)
2. Enable **Compute Engine API**:
   ```
   APIs & Services → Enable APIs → search "Compute Engine API" → Enable
   ```
3. Create Service Account:
   ```
   IAM & Admin → Service Accounts → Create Service Account
   - Name: github-actions
   - Role: Compute Admin + Service Account User
   ```
4. Create a JSON key:
   ```
   Click the service account → Keys → Add Key → Create new key → JSON
   ```
   Download the JSON file. This is your `GCP_SA_KEY`.

### P2. Add GitHub Secrets

Go to repo **Settings → Secrets and variables → Actions** and add:

| Secret | Value | Source |
|--------|-------|--------|
| `GCP_SA_KEY` | Full JSON content of the service account key file | GCP IAM (P1 step 4) |
| `GCP_PROJECT_ID` | Your GCP project ID (e.g. `fashion-rental-123456`) | GCP Console |
| `CLOUDFLARE_API_TOKEN` | API token with Pages:Edit + R2:Edit permissions | Cloudflare → My Profile → API Tokens |
| `CLOUDFLARE_ACCOUNT_ID` | Your Cloudflare account ID | Cloudflare dashboard URL |
| `VITE_API_URL` | `https://api.yourshop.in` | Your domain |
| `DATABASE_URL` | `jdbc:postgresql://ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require` | Neon dashboard (after TASK-2) |
| `DATABASE_USERNAME` | Neon username | Neon dashboard (after TASK-2) |
| `DATABASE_PASSWORD` | Neon password | Neon dashboard (after TASK-2) |
| `JWT_SECRET` | `openssl rand -base64 48` | Generate locally |
| `APP_USERNAME` | `admin` (or your choice) | Choose |
| `APP_PASSWORD` | Strong password | Choose |
| `ALLOWED_ORIGINS` | `https://yourshop.in` | Your domain |
| `R2_ACCOUNT_ID` | Cloudflare account ID | Cloudflare R2 (after TASK-3) |
| `R2_ACCESS_KEY_ID` | R2 API token access key | Cloudflare R2 (after TASK-3) |
| `R2_SECRET_ACCESS_KEY` | R2 API token secret | Cloudflare R2 (after TASK-3) |
| `R2_BUCKET_NAME` | `fashion-rental` | Cloudflare R2 (after TASK-3) |
| `R2_PUBLIC_URL_BASE` | `https://pub-xxx.r2.dev` | Cloudflare R2 (after TASK-3) |
| `NEON_PG_URL` | `postgresql://user:pass@ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require` | Neon dashboard (non-JDBC format, for pg_dump) |

---

## Task Breakdown

---

### TASK-1: GCP VM Provisioning via GitHub Actions

**File**: `.github/workflows/infra-provision.yml`

One-time workflow to create the GCP e2-micro VM, install Docker, and configure the deploy user. Run via manual trigger (`workflow_dispatch`).

```yaml
name: Provision GCP Infrastructure

on:
  workflow_dispatch:
    inputs:
      action:
        description: 'Action to perform'
        required: true
        default: 'create'
        type: choice
        options:
          - create
          - destroy

jobs:
  provision:
    name: Provision GCP VM
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - id: auth
        uses: google-github-actions/auth@v2
        with:
          credentials_json: ${{ secrets.GCP_SA_KEY }}

      - uses: google-github-actions/setup-gcloud@v2
        with:
          project_id: ${{ secrets.GCP_PROJECT_ID }}

      - name: Create static IP
        if: inputs.action == 'create'
        run: |
          gcloud compute addresses create fashion-rental-ip \
            --region=us-central1 \
            || echo "IP already exists"
          echo "STATIC_IP=$(gcloud compute addresses describe fashion-rental-ip \
            --region=us-central1 --format='value(address)')" >> $GITHUB_ENV

      - name: Create VM
        if: inputs.action == 'create'
        run: |
          gcloud compute instances create fashion-rental-backend \
            --zone=us-central1-a \
            --machine-type=e2-micro \
            --image-family=debian-12 \
            --image-project=debian-cloud \
            --boot-disk-size=30GB \
            --boot-disk-type=pd-standard \
            --tags=http-server,https-server \
            --address=${{ env.STATIC_IP }} \
            --metadata=startup-script='#!/bin/bash
              # Install Docker
              apt-get update
              apt-get install -y ca-certificates curl gnupg rclone lsb-release wget

              # Add PostgreSQL APT repo (Debian 12 only ships pg15; we need pg16 client for Neon)
              curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /etc/apt/keyrings/postgresql.gpg
              echo "deb [signed-by=/etc/apt/keyrings/postgresql.gpg] http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list

              # Add Docker APT repo
              install -m 0755 -d /etc/apt/keyrings
              curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
              chmod a+r /etc/apt/keyrings/docker.gpg
              echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list

              apt-get update
              apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin postgresql-client-16

              # App directory
              mkdir -p /opt/app
              '

      - name: Create firewall rules
        if: inputs.action == 'create'
        run: |
          gcloud compute firewall-rules create allow-http \
            --allow=tcp:80 --target-tags=http-server \
            || echo "Rule already exists"
          gcloud compute firewall-rules create allow-https \
            --allow=tcp:443 --target-tags=https-server \
            || echo "Rule already exists"

      - name: Wait for startup script to complete
        if: inputs.action == 'create'
        run: |
          echo "Waiting 120s for startup script to finish..."
          sleep 120

      - name: Configure SSH user for Docker access
        if: inputs.action == 'create'
        run: |
          gcloud compute ssh fashion-rental-backend \
            --zone=us-central1-a \
            --command="sudo usermod -aG docker \$USER && sudo chown \$USER:\$USER /opt/app"

      - name: Verify Docker installation
        if: inputs.action == 'create'
        run: |
          gcloud compute ssh fashion-rental-backend \
            --zone=us-central1-a \
            --command="docker --version && docker compose version"

      - name: Print static IP
        if: inputs.action == 'create'
        run: |
          echo "============================================"
          echo "VM created successfully!"
          echo "Static IP: ${{ env.STATIC_IP }}"
          echo "Add this IP as an A record for api.yourshop.in in Cloudflare DNS"
          echo "============================================"

      - name: Destroy VM
        if: inputs.action == 'destroy'
        run: |
          gcloud compute instances delete fashion-rental-backend \
            --zone=us-central1-a --quiet || true
          gcloud compute addresses delete fashion-rental-ip \
            --region=us-central1 --quiet || true
```

**Note on static IP cost**: Free while attached to a running VM. If you stop the VM, it costs ~$0.004/hour (~$3/month). Never stop the VM — use it or destroy it.

---

### TASK-2: Provision Neon PostgreSQL

**Manual — Neon Console**

1. Sign up at [neon.tech](https://neon.tech) → **Create Project**
2. Settings:
   - Project name: `fashion-rental`
   - PostgreSQL version: **16**
   - Region: **AWS us-east-1** (low latency to GCP us-central1 — same continent)
3. After creation, go to **Connection Details** → copy:
   - **JDBC URL** (for Spring Boot): `jdbc:postgresql://ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require`
   - **PostgreSQL URL** (for pg_dump): `postgresql://fashion_user:xxx@ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require`
4. Add both to GitHub Secrets:
   - `DATABASE_URL` = the JDBC URL
   - `DATABASE_USERNAME` = username from Neon
   - `DATABASE_PASSWORD` = password from Neon
   - `NEON_PG_URL` = the PostgreSQL URL (non-JDBC, for pg_dump)
5. In Neon dashboard → **Settings** → **IP Allow** → add your GCP VM's static external IP to restrict access

---

### TASK-3: Cloudflare R2 Bucket Setup

**Manual — Cloudflare Dashboard**

1. Cloudflare Dashboard → **R2** → **Create bucket**
   - Name: `fashion-rental`
   - Location: **APAC** (closest to end users in India for serving photos)
2. **Settings** → **Public Access** → **Allow Access** (needed to serve item photos publicly)
3. Note the **Public Bucket URL**: `https://pub-xxx.r2.dev`
4. **R2** → **Manage R2 API Tokens** → **Create API Token**
   - Permissions: **Object Read & Write** on bucket `fashion-rental`
   - Copy: `Account ID`, `Access Key ID`, `Secret Access Key`
5. Add all values to GitHub Secrets (see Prerequisites table)

---

### TASK-4: Cloudflare Pages Setup (Frontend)

**Manual — Cloudflare Dashboard**

Since we deploy via GitHub Actions (wrangler), Cloudflare Pages just needs the project created — no GitHub integration.

1. Install wrangler locally (one-time): `npm i -g wrangler`
2. Authenticate: `wrangler login`
3. Create the Pages project:
   ```bash
   wrangler pages project create fashion-rental-frontend --production-branch=main
   ```
   Or via Cloudflare Dashboard → **Pages** → **Create a project** → **Direct Upload** (skip the Git integration — deploys come from GitHub Actions).

4. **Do NOT connect to GitHub** — we deploy exclusively via the CD workflow to ensure tests pass first.

---

### TASK-5: Domain + Cloudflare DNS Setup

**Manual**

#### 5a. Register domain
1. [Namecheap](https://namecheap.com) → search `yourshopname.in` (~₹900/year)
2. Purchase → **Nameservers** → **Custom DNS**

#### 5b. Add to Cloudflare
1. Cloudflare Dashboard → **Add a Site** → enter domain → **Free plan**
2. Copy the two Cloudflare nameserver hostnames
3. Paste into Namecheap Custom DNS fields
4. Wait 15-30 minutes for propagation

#### 5c. DNS records

| Type | Name | Target | Proxy |
|------|------|--------|-------|
| A | `api` | `<gcp-vm-static-ip>` | Proxied (orange cloud) |
| CNAME | `@` (root) | `fashion-rental-frontend.pages.dev` | Proxied (orange cloud) |

#### 5d. Custom domain on Cloudflare Pages
Pages project → **Custom domains** → add `yourshop.in`

#### 5e. SSL mode
Cloudflare Dashboard → **SSL/TLS** → set to **Full** (not Full Strict — GCP VM has no TLS cert, Cloudflare terminates SSL at the edge)

---

### TASK-6: Backend Dockerfile + .dockerignore

**File**: `backend/.dockerignore`

Excludes unnecessary files from the Docker build context for faster builds and smaller images.

```
.git
.gradle
build
*.md
.idea
.vscode
.env*
```

**File**: `backend/Dockerfile`

Multi-stage build. GitHub Actions builds this image and pushes to ghcr.io.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /app/build/libs/fashion-rental-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-Xms256m", "-Xmx400m", "-jar", "app.jar"]
```

**Notes**:
- Multi-stage keeps the final image small (~200MB vs ~600MB with JDK)
- `-Xms256m -Xmx400m` keeps heap under e2-micro's 1GB RAM (container + OS ≈ 600MB)
- Dependencies are cached in a separate layer for faster rebuilds
- `HEALTHCHECK` detects hung JVM — Docker will restart container if health fails 3 consecutive times
- `wget` is installed in runtime stage for health check (Alpine doesn't include `curl`)
- No Nginx needed — Docker maps container port 8080 to host port 80 (`-p 80:8080`)

**RAM budget on e2-micro (1GB)**:
```
OS + systemd:       ~150MB
Docker daemon:      ~100MB
JVM (-Xmx400m):    ~500-550MB (heap + metaspace + thread stacks)
────────────────────────────
Total:              ~750-800MB / 1024MB
Headroom:           ~200MB
```
If OOM-kills occur, reduce JVM heap to `-Xms200m -Xmx350m`.

---

### TASK-7: CI Workflow

**File**: `.github/workflows/ci.yml`

**Note**: This file already exists on the `infra/render-deployment-plan` branch with Render deploy hooks. **Replace it entirely** with the version below. The old workflow has a `deploy` job that triggers Render — that's no longer applicable. CI should only run tests.

Runs automatically on PRs and pushes to main. Tests only — no deployment.

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend-test:
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

  frontend-test:
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
```

---

### TASK-8: CD Workflow (Manual Trigger)

**File**: `.github/workflows/cd.yml`

Deploys backend and frontend. **Only runs on manual trigger** (`workflow_dispatch`). You can choose to deploy backend, frontend, or both.

```yaml
name: CD — Deploy

on:
  workflow_dispatch:
    inputs:
      target:
        description: 'What to deploy'
        required: true
        default: 'all'
        type: choice
        options:
          - all
          - backend
          - frontend

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository_owner }}/fashion-rental-backend

jobs:
  verify-ci:
    name: Verify CI passed
    runs-on: ubuntu-latest
    steps:
      - name: Check latest CI status on main
        run: |
          STATUS=$(gh run list --repo ${{ github.repository }} --branch main --workflow ci.yml --limit 1 --json conclusion -q '.[0].conclusion')
          if [ "$STATUS" != "success" ]; then
            echo "❌ CI has not passed on main (status: $STATUS). Aborting deploy."
            exit 1
          fi
          echo "✅ CI passed on main. Proceeding with deploy."
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  deploy-backend:
    name: Deploy backend → GCP
    runs-on: ubuntu-latest
    needs: [verify-ci]
    if: inputs.target == 'all' || inputs.target == 'backend'
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}

      - id: auth
        uses: google-github-actions/auth@v2
        with:
          credentials_json: ${{ secrets.GCP_SA_KEY }}

      - uses: google-github-actions/setup-gcloud@v2
        with:
          project_id: ${{ secrets.GCP_PROJECT_ID }}

      - name: Deploy to GCP VM
        run: |
          gcloud compute ssh fashion-rental-backend \
            --zone=us-central1-a \
            --command="
              docker pull ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest

              # Stop existing container (if running)
              docker stop fashion-rental-backend 2>/dev/null || true
              docker rm fashion-rental-backend 2>/dev/null || true

              # Run new container with health check
              docker run -d \
                --name fashion-rental-backend \
                --restart unless-stopped \
                -p 80:8080 \
                --health-cmd='wget -qO- http://localhost:8080/actuator/health || exit 1' \
                --health-interval=30s \
                --health-timeout=5s \
                --health-start-period=60s \
                --health-retries=3 \
                --log-opt max-size=50m \
                --log-opt max-file=3 \
                -e DATABASE_URL='${{ secrets.DATABASE_URL }}' \
                -e DATABASE_USERNAME='${{ secrets.DATABASE_USERNAME }}' \
                -e DATABASE_PASSWORD='${{ secrets.DATABASE_PASSWORD }}' \
                -e JWT_SECRET='${{ secrets.JWT_SECRET }}' \
                -e APP_USERNAME='${{ secrets.APP_USERNAME }}' \
                -e APP_PASSWORD='${{ secrets.APP_PASSWORD }}' \
                -e ALLOWED_ORIGINS='${{ secrets.ALLOWED_ORIGINS }}' \
                -e R2_ACCOUNT_ID='${{ secrets.R2_ACCOUNT_ID }}' \
                -e R2_ACCESS_KEY_ID='${{ secrets.R2_ACCESS_KEY_ID }}' \
                -e R2_SECRET_ACCESS_KEY='${{ secrets.R2_SECRET_ACCESS_KEY }}' \
                -e R2_BUCKET_NAME='${{ secrets.R2_BUCKET_NAME }}' \
                -e R2_PUBLIC_URL_BASE='${{ secrets.R2_PUBLIC_URL_BASE }}' \
                -e SPRING_PROFILES_ACTIVE=prod \
                ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest

              # Wait and verify health
              sleep 30
              docker ps | grep fashion-rental-backend
              docker logs --tail 20 fashion-rental-backend

              # Prune images older than 7 days (keep recent for rollback)
              docker image prune -af --filter 'until=168h'
            "

  deploy-frontend:
    name: Deploy frontend → Cloudflare Pages
    runs-on: ubuntu-latest
    needs: [verify-ci]
    if: inputs.target == 'all' || inputs.target == 'frontend'
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

      - name: Build
        run: cd frontend && pnpm build
        env:
          VITE_API_URL: ${{ secrets.VITE_API_URL }}

      - name: Deploy to Cloudflare Pages
        uses: cloudflare/wrangler-action@v3
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          command: pages deploy frontend/dist --project-name=fashion-rental-frontend
```

**Container deployment benefits** (vs JAR copy):
- No Java, Gradle, or Nginx installed on the VM — just Docker
- No old JARs accumulating — `docker image prune` cleans up (keeps last 7 days for rollback)
- No systemd service needed — Docker `--restart unless-stopped` handles restarts
- Built-in health check — hung JVM detected and container restarted automatically
- Log rotation handled — `--log-opt max-size=50m --log-opt max-file=3` prevents disk fill
- Consistent environment — same image runs locally and in production
- Rollback = `docker run` with the previous `:<sha>` tag

**Rollback procedure**:
```bash
# SSH into VM
gcloud compute ssh fashion-rental-backend --zone=us-central1-a

# Find the previous image SHA
docker images ghcr.io/satyambaldawa/fashion-rental-backend --format "{{.Tag}} {{.CreatedAt}}"

# Stop current, run previous
docker stop fashion-rental-backend && docker rm fashion-rental-backend
docker run -d --name fashion-rental-backend ... ghcr.io/satyambaldawa/fashion-rental-backend:<previous-sha>
```

---

### TASK-9: pg_dump Backup Cron (GCP VM)

Set up after VM is provisioned (TASK-1). Run via `gcloud compute ssh`.

```bash
# SSH into the VM
gcloud compute ssh fashion-rental-backend --zone=us-central1-a

# Verify pg_dump client (installed by startup script)
pg_dump --version

# Set timezone on VM (for cron scheduling)
sudo timedatectl set-timezone Asia/Kolkata

# Configure rclone for Cloudflare R2
rclone config
# → New remote
# → name: r2
# → provider: S3
# → Cloudflare
# → Paste: access_key_id, secret_access_key
# → endpoint: https://<account-id>.r2.cloudflarestorage.com

# Create env file for backup credentials (use values from GitHub secret NEON_PG_URL)
# NEON_PG_URL format: postgresql://user:password@ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require
cat > /opt/app/.backup.env << 'ENVFILE'
NEON_PG_URL="postgresql://YOUR_USER:YOUR_PASSWORD@YOUR_NEON_HOST/fashion_rental?sslmode=require"
ENVFILE
chmod 600 /opt/app/.backup.env
# IMPORTANT: Edit .backup.env and paste the actual NEON_PG_URL value from GitHub secrets

# Create backup script
cat > /opt/app/backup.sh << 'SCRIPT'
#!/bin/bash
source /opt/app/.backup.env
pg_dump "$NEON_PG_URL" \
  | gzip \
  | rclone rcat r2:fashion-rental/backups/db-$(date +%Y-%m-%d).sql.gz

# Keep only last 30 days of backups
rclone delete r2:fashion-rental/backups/ --min-age 30d
SCRIPT
chmod +x /opt/app/backup.sh

# Add cron job (runs at 2am IST daily — timezone set above)
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/app/backup.sh >> /opt/app/backup.log 2>&1") | crontab -
```

**Notes**:
- Uses `pg_dump` with native PostgreSQL URL (not JDBC format)
- VM timezone set to IST so `0 2 * * *` runs at 2am IST
- `postgresql-client-16` is installed by the VM startup script (TASK-1)
- `rclone` is installed by the VM startup script (TASK-1)
- Compressed dumps are ~1-5MB — negligible GCP egress and R2 storage
- Auto-prunes backups older than 30 days

---

### TASK-10: Backend Code Changes

**Branch strategy**: Items 10a and 10b are already implemented on the `infra/render-deployment-plan` branch. Items 10c and 10d are new changes. The agent should:

1. Work on the `infra/render-deployment-plan` branch (or a branch created from it)
2. Verify 10a and 10b are already correct (read and confirm — do not re-implement)
3. Implement 10c and 10d as new changes on the same branch
4. Delete `render.yaml` (Render-specific, no longer applicable) and `infra/render-deployment-plan.md.archived` if they still exist
5. Delete the old Render deploy job from `.github/workflows/ci.yml` if it hasn't been replaced by TASK-7 yet

#### 10a. CORS — configurable via env var (ALREADY DONE — verify only)

Verify these changes exist on the branch:

`backend/src/main/java/com/fashionrental/config/CorsConfig.java` — reads origins from `@Value("${app.cors.allowed-origins}")` instead of hardcoded `*`.

`backend/src/main/resources/application.yml` (line 29-30):
```yaml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS}
```

`backend/src/main/resources/application-dev.yml`:
```yaml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173}
```

**Production env var**: `ALLOWED_ORIGINS=https://yourshop.in`

#### 10b. Frontend API base URL — env var driven (ALREADY DONE — verify only)

Verify this change exists on the branch:

`frontend/src/api/client.ts` (line 5):
```ts
baseURL: `${import.meta.env.VITE_API_URL ?? ''}/api`
```

- Dev: `VITE_API_URL` unset → `/api` → Vite proxy handles it. No local change.
- Prod: `VITE_API_URL=https://api.yourshop.in` → direct HTTPS call.

#### 10c. SPA fallback for Cloudflare Pages (NEW — implement)

Create file `frontend/public/_redirects` with this exact content:
```
/* /index.html 200
```

This ensures React Router works on direct URL loads (e.g. `yourshop.in/inventory` → serves `index.html`, React handles routing).

#### 10d. HikariCP connection pool tuning for Neon (NEW — implement)

Neon free tier **auto-suspends compute after 5 minutes of inactivity**. When this happens, HikariCP's pooled connections silently die. Without tuning, the first request after suspend hits stale connections → errors or multi-second delays.

**File**: `backend/src/main/resources/application.yml`

Add `hikari` block under the existing `spring.datasource` section (after `driver-class-name` on line 12). The result should look like:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5          # e2-micro doesn't need 10 (default)
      connection-timeout: 10000     # 10s — allow time for Neon cold start (~1-3s)
      max-lifetime: 300000          # 5 min — discard connections before Neon suspends
      keepalive-time: 60000         # 1 min — detect stale connections early
      validation-timeout: 5000      # 5s — timeout for connection validation
```

**Why each setting matters**:
- `max-lifetime=300000` (5 min): Connections are recycled before Neon's 5-min suspend window, so the pool never holds truly stale connections overnight.
- `keepalive-time=60000` (1 min): Sends a validation query every minute to detect dead connections before they're handed to application code.
- `connection-timeout=10000` (10s): Gives enough time for Neon cold start if all connections are stale and new ones must be created.
- `maximum-pool-size=5`: Reduces from default 10 — e2-micro doesn't need 10 concurrent DB connections for a single-user app.

---

### TASK-11: Go-Live Validation Checklist

Run through in order after DNS propagates and backend container is running.

```
Infrastructure
[ ] https://yourshop.in loads without certificate warning
[ ] https://api.yourshop.in/actuator/health returns {"status":"UP"}
[ ] https://yourshop.in/swagger-ui.html returns 404 (Swagger disabled in prod)
[ ] Cloudflare shows both domains as Proxied (orange cloud)
[ ] docker ps on GCP VM shows fashion-rental-backend running

Authentication
[ ] Login with APP_USERNAME / APP_PASSWORD succeeds, returns JWT
[ ] Invalid credentials return 401
[ ] Accessing protected endpoint without token returns 401

CORS
[ ] API call from yourshop.in to api.yourshop.in succeeds
[ ] Preflight from a different origin returns 403

Core Flows
[ ] Browse inventory — items load
[ ] Create a new item with a photo — photo URL uses R2 domain (pub-xxx.r2.dev)
[ ] Check availability for an item
[ ] Create a customer
[ ] Create a checkout receipt
[ ] Process a return, generate invoice
[ ] View reports

PWA Install
[ ] Open https://yourshop.in in Chrome on Android tablet
[ ] Browser shows "Add to Home Screen" prompt
[ ] Install — icon appears on home screen
[ ] Launch from home screen — runs fullscreen, no browser chrome

Deployment Pipeline
[ ] Run CD workflow (manual trigger) for backend — Docker image pushed, container restarted
[ ] Run CD workflow (manual trigger) for frontend — deployed to Cloudflare Pages
[ ] Verify new version is live after deploy
[ ] Run CI workflow on a PR — tests pass, no deploy happens

Backup
[ ] Run /opt/app/backup.sh manually
[ ] Verify backup file appears in R2: fashion-rental/backups/db-YYYY-MM-DD.sql.gz
[ ] Download and test restoring the backup against a local PostgreSQL
```

---

## Environment Variable Reference

### GitHub Secrets (single source of truth for all env vars)

All environment variables are stored as GitHub Secrets. The CD workflow injects them into the Docker container at runtime.

| Secret | Example | Source |
|--------|---------|--------|
| **GCP** | | |
| `GCP_SA_KEY` | `{"type":"service_account",...}` | GCP IAM (P1) |
| `GCP_PROJECT_ID` | `fashion-rental-123456` | GCP Console |
| **Cloudflare** | | |
| `CLOUDFLARE_API_TOKEN` | `xxx` | Cloudflare API Tokens |
| `CLOUDFLARE_ACCOUNT_ID` | `abc123` | Cloudflare dashboard |
| `VITE_API_URL` | `https://api.yourshop.in` | Your domain |
| **Database** | | |
| `DATABASE_URL` | `jdbc:postgresql://ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require` | Neon dashboard |
| `DATABASE_USERNAME` | `fashion_user` | Neon dashboard |
| `DATABASE_PASSWORD` | `xxx` | Neon dashboard |
| `NEON_PG_URL` | `postgresql://user:pass@ep-xxx.us-east-1.aws.neon.tech/fashion_rental?sslmode=require` | Neon dashboard (non-JDBC, for pg_dump) |
| **Application** | | |
| `JWT_SECRET` | 48-char base64 string | `openssl rand -base64 48` |
| `APP_USERNAME` | `admin` | Choose |
| `APP_PASSWORD` | strong password | Choose |
| `ALLOWED_ORIGINS` | `https://yourshop.in` | Your domain |
| **Cloudflare R2** | | |
| `R2_ACCOUNT_ID` | `abc123` | Cloudflare R2 |
| `R2_ACCESS_KEY_ID` | `xyz` | Cloudflare R2 API token |
| `R2_SECRET_ACCESS_KEY` | `secret` | Cloudflare R2 API token |
| `R2_BUCKET_NAME` | `fashion-rental` | R2 bucket name |
| `R2_PUBLIC_URL_BASE` | `https://pub-xxx.r2.dev` | R2 public bucket URL |

---

## Accepted Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Secrets visible in `docker inspect` on VM | Anyone with SSH access to the VM can see all env vars via `docker inspect` or `/proc/<pid>/environ` | VM is private, single-user. Acceptable at this scale. Proper fix = GCP Secret Manager (overkill here). |
| RAM pressure on e2-micro (1GB) | JVM + Docker + OS uses ~800MB of 1GB. Photo uploads temporarily increase pressure. | Monitor for OOM-kills. If they occur, reduce JVM to `-Xmx350m`. |
| Neon free tier compute limits (191.9 hrs/month) | If exceeded, DB suspends until next month | Single-user app with auto-suspend = ~30-60 hrs/month actual usage. Monitor in Neon dashboard. |

---

## Tech Debt

| Item | Description | Priority |
|------|-------------|----------|
| Presigned R2 uploads | Move photo upload from backend-as-middleman to direct tablet → R2 using presigned URLs. Eliminates GCP egress for photos entirely. | Low — current egress is negligible at this scale |
