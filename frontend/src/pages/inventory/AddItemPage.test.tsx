import { describe, it, expect } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, waitFor } from '../../test/render'
import { server } from '../../test/server'
import * as f from '../../test/factories'
import { STALE_VERSION_MESSAGE } from '../../utils/staleVersion'
import AddItemPage from './AddItemPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })
const staleResponse = () =>
  HttpResponse.json({ success: false, data: null, error: STALE_VERSION_MESSAGE }, { status: 409 })

const renderEditPage = () =>
  renderWithProviders(<AddItemPage />, { route: '/inventory/item-1/edit', path: '/inventory/:id/edit' })

/** GET returns the version for the Nth load (1-based), the last one repeating. */
function serveVersions(versions: number[]) {
  const loads = { count: 0 }
  server.use(http.get('*/api/items/:id', () => {
    const version = versions[Math.min(loads.count, versions.length - 1)]
    loads.count += 1
    return ok(f.anItemDetail({ id: 'item-1', name: `Royal Sherwani v${version}`, version }))
  }))
  return loads
}

function capturePuts(respond: (attempt: number) => Response) {
  const bodies: Array<{ version?: number; name?: string }> = []
  server.use(http.put('*/api/items/:id', async ({ request }) => {
    bodies.push(await request.json() as { version?: number; name?: string })
    return respond(bodies.length)
  }))
  return bodies
}

async function renderLoaded() {
  renderEditPage()
  await flush()
  await screen.findByDisplayValue(/Royal Sherwani/)
  return userEvent.setup()
}

const save = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: 'Save Changes' }))

describe('AddItemPage edit — optimistic locking (#189)', () => {
  it('sends the version captured when the item was loaded', async () => {
    serveVersions([4])
    const puts = capturePuts(() => ok(f.anItemDetail({ version: 5 })))

    const user = await renderLoaded()
    await save(user)

    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0].version).toBe(4)
  })

  it('keeps sending the loaded version even after a background refetch brings in a newer one', async () => {
    const loads = serveVersions([4, 9])
    const puts = capturePuts(() => ok(f.anItemDetail({ version: 10 })))

    const user = await renderLoaded()
    window.dispatchEvent(new Event('visibilitychange'))
    await waitFor(() => expect(loads.count).toBeGreaterThanOrEqual(2))
    await flush()
    await save(user)

    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0].version).toBe(4)
  })

  it('shows the server message and a Reload button when the item was changed by someone else', async () => {
    serveVersions([4])
    capturePuts(() => staleResponse())

    const user = await renderLoaded()
    await save(user)

    expect(await screen.findByText(STALE_VERSION_MESSAGE)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
    expect(screen.queryByText('Failed to update item. Please try again.')).not.toBeInTheDocument()
  })

  it('after Reload the form shows the latest item and the next save sends the new version', async () => {
    serveVersions([4, 5])
    const puts = capturePuts(attempt => (attempt === 1 ? staleResponse() : ok(f.anItemDetail({ version: 6 }))))

    const user = await renderLoaded()
    await save(user)
    await user.click(await screen.findByRole('button', { name: 'Reload' }))
    await screen.findByDisplayValue('Royal Sherwani v5')
    await save(user)

    await waitFor(() => expect(puts).toHaveLength(2))
    expect(puts[0].version).toBe(4)
    expect(puts[1].version).toBe(5)
    expect(puts[1].name).toBe('Royal Sherwani v5')
  })

  it('keeps the Reload prompt and shows an error when the reload itself fails', async () => {
    const loads = { count: 0 }
    server.use(http.get('*/api/items/:id', () => {
      loads.count += 1
      return loads.count === 1
        ? ok(f.anItemDetail({ id: 'item-1', name: 'Royal Sherwani', version: 4 }))
        : new HttpResponse(null, { status: 500 })
    }))
    capturePuts(() => staleResponse())

    const user = await renderLoaded()
    await save(user)
    await user.click(await screen.findByRole('button', { name: 'Reload' }))

    expect(await screen.findByText('Could not reload the item. Check your connection and try again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
  })

  it('tells staff to reload the page when a cached old app sent no version', async () => {
    serveVersions([4])
    capturePuts(() => HttpResponse.json(
      { success: false, data: null, error: 'version: must not be null' },
      { status: 400 },
    ))

    const user = await renderLoaded()
    await save(user)

    expect(await screen.findByText('The app was updated. Reload the page and try again.')).toBeInTheDocument()
  })

  it('keeps the generic failure message for other errors', async () => {
    serveVersions([4])
    capturePuts(() => HttpResponse.json({ success: false, data: null, error: 'boom' }, { status: 500 }))

    const user = await renderLoaded()
    await save(user)

    expect(await screen.findByText('Failed to update item. Please try again.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reload' })).not.toBeInTheDocument()
  })
})
