# SETUP-02: React PWA Frontend Scaffold

**Type:** Setup Task
**Priority:** P0 — Must complete before any frontend feature work
**Depends On:** SETUP-01 (API must be running for integration)
**Blocks:** All frontend feature stories

---

## Goal

Scaffold the React PWA frontend with routing, API client, auth flow, layout shell, and PWA manifest. A developer should be able to run the frontend, log in, and see the app shell after this task.

---

## Tech Choices

| Concern | Choice |
|---------|--------|
| Build tool | Vite 5.x |
| Language | TypeScript 5.x |
| UI components | Ant Design 5.x |
| Routing | React Router v6 |
| Server state | TanStack Query (React Query) v5 |
| HTTP client | Axios |
| Form validation | Zod + React Hook Form |
| Date handling | Day.js |
| Styling | Ant Design + CSS Modules for custom styles |

---

## Project Structure

```
frontend/
  public/
    manifest.json         ← PWA manifest
    icons/
      icon-192.png
      icon-512.png
  src/
    main.tsx              ← entry point
    App.tsx               ← router setup
    api/
      client.ts           ← Axios instance with auth interceptor
      auth.ts
      items.ts
      customers.ts
      checkout.ts
      receipts.ts
      invoices.ts
      reports.ts
      config.ts
    components/
      layout/
        AppLayout.tsx     ← sidebar nav + content area
        Sidebar.tsx
      common/
        PageHeader.tsx
        LoadingSpinner.tsx
        ErrorMessage.tsx
        AmountDisplay.tsx ← formats paise → ₹X,XXX.XX
    pages/
      LoginPage.tsx
      inventory/
      customers/
      checkout/
      receipts/
      returns/
      reports/
      config/
    hooks/
      useAuth.ts
    utils/
      currency.ts         ← paise ↔ INR conversion
      datetime.ts         ← format datetimes for display and API
    types/
      api.ts              ← mirrors backend DTOs
    store/
      authStore.ts        ← login token in localStorage
  index.html
  vite.config.ts
  tsconfig.json
  package.json
```

---

## PWA Setup

### public/manifest.json

```json
{
  "name": "Fashion Rental",
  "short_name": "Rentals",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#1677ff",
  "icons": [
    { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

### vite.config.ts

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: false,                   // using public/manifest.json
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg}'],
        runtimeCaching: []               // no API caching in MVP
      }
    })
  ],
  server: {
    proxy: {
      '/api': 'http://localhost:8080'    // proxy to Spring Boot in dev
    }
  }
})
```

---

## API Client

### src/api/client.ts

```ts
import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
})

// Attach JWT token to every request
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Redirect to login on 401
client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('jwt')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default client
```

---

## Currency Utility

### src/utils/currency.ts

```ts
// All amounts from the API are in paise (integer). Convert for display only.

export function paisaToRupees(paise: number): number {
  return paise / 100
}

export function rupeesToPaise(rupees: number): number {
  return Math.round(rupees * 100)
}

export function formatCurrency(paise: number): string {
  return `₹${paisaToRupees(paise).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}
```

### src/components/common/AmountDisplay.tsx

```tsx
export function AmountDisplay({ paise, className }: { paise: number; className?: string }) {
  return <span className={className}>{formatCurrency(paise)}</span>
}
```

---

## Datetime Utility

### src/utils/datetime.ts

```ts
import dayjs from 'dayjs'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'
dayjs.extend(utc)
dayjs.extend(timezone)

const IST = 'Asia/Kolkata'

// Format for display in the UI
export function formatDatetime(iso: string): string {
  return dayjs(iso).tz(IST).format('DD MMM YYYY, hh:mm A')
}

export function formatDate(iso: string): string {
  return dayjs(iso).tz(IST).format('DD MMM YYYY')
}

// Convert a DateTimePicker value to ISO 8601 with IST offset for the API
export function toApiDatetime(date: dayjs.Dayjs): string {
  return date.tz(IST).toISOString()
}
```

---

## Routing (App.tsx)

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import LoginPage from './pages/LoginPage'
import AppLayout from './components/layout/AppLayout'
import { useAuthStore } from './store/authStore'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } }
})

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token)
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={{ token: { colorPrimary: '#1677ff' } }}>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/*" element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            } />
          </Routes>
        </BrowserRouter>
      </ConfigProvider>
    </QueryClientProvider>
  )
}
```

---

## Login Page

### src/pages/LoginPage.tsx

```tsx
// POST /api/auth/login → { token }
// Store token in localStorage + authStore
// Redirect to / on success
```

### Backend Auth Endpoint (implement in SETUP-01)

```
POST /api/auth/login
Body: { username: string, password: string }
Response: { success: true, data: { token: string } }
```

Spring Security: configure a single user from environment variables (`APP_USERNAME`, `APP_PASSWORD`). No database table for users in MVP.

---

## Sidebar Navigation

```
📦 Inventory       → /inventory
👥 Customers       → /customers
🧾 New Rental      → /checkout
📋 Active Rentals  → /receipts
↩️  Process Return  → /receipts (filtered to active)
📊 Reports         → /reports
⚙️  Settings        → /settings
```

---

## Acceptance Criteria

- [ ] `npm run dev` starts the frontend on port 5173 with Vite proxy to Spring Boot
- [ ] `npm run build` produces a production build with PWA service worker
- [ ] Opening `http://localhost:5173` redirects to `/login` when not authenticated
- [ ] Logging in with correct credentials stores the JWT and redirects to `/inventory`
- [ ] Logging in with wrong credentials shows an error message
- [ ] The sidebar renders all navigation links
- [ ] `formatCurrency(150000)` returns `"₹1,500.00"`
- [ ] `formatCurrency(0)` returns `"₹0.00"`
- [ ] Refreshing the page with a valid token keeps the user logged in
- [ ] On Android Chrome: manifest is detected, "Add to Home Screen" prompt is available
