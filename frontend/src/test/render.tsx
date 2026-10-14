import type { ReactElement } from 'react'
import { act, render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes, parsePath } from 'react-router-dom'

interface Options {
  route?: string
  /** Route pattern to mount `ui` under, so pages using useParams receive them. */
  path?: string
  /** Router location state, e.g. a back link handed over by the page that navigated here. */
  state?: unknown
}

export function renderWithProviders(ui: ReactElement, { route = '/', path, state }: Options = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={[{ ...parsePath(route), state }]}>
          {path ? <Routes><Route path={path} element={ui} /></Routes> : ui}
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  )
}

/** Lets pending queries/effects resolve so the data-loaded render path executes. */
export async function flush(ms = 60) {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms))
  })
}

export * from '@testing-library/react'
