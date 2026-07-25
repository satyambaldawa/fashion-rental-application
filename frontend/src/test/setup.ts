import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { server } from './server'

// jsdom lacks several browser APIs that Ant Design relies on — stub them.
class MockObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
const globalWithObservers = globalThis as unknown as {
  ResizeObserver: unknown
  IntersectionObserver: unknown
}
globalWithObservers.ResizeObserver = MockObserver
globalWithObservers.IntersectionObserver = MockObserver

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
})
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo

// The 401 interceptor sets window.location.href to redirect to /login. jsdom cannot
// navigate and logs a noisy "Not implemented" error; swallow href assignments while
// leaving reads (origin, pathname) intact for URL resolution.
try {
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: new Proxy(window.location, { set: () => true }),
  })
} catch {
  /* location not redefinable in this environment — tests still pass, just noisier */
}

beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
