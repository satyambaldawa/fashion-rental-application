import { describe, it, expect, beforeEach } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { renderWithProviders, flush, screen, waitFor } from '../test/render'
import { server } from '../test/server'
import * as f from '../test/factories'
import { useAuthStore } from '../store/authStore'
import { STALE_VERSION_MESSAGE } from '../utils/staleVersion'
import SettingsPage from './SettingsPage'

const ok = (data: unknown) => HttpResponse.json({ success: true, data, error: null })

type SavedBody = { rules: Array<{ id: string | null; version: number | null; penaltyMultiplier: number }> }

beforeEach(() => useAuthStore.setState({ token: 'test-token', role: 'OWNER' }))

function serveRules(versionsPerLoad: number[]) {
  const loads = { count: 0 }
  server.use(http.get('*/api/config/late-fee-rules', () => {
    const version = versionsPerLoad[Math.min(loads.count, versionsPerLoad.length - 1)]
    loads.count += 1
    return ok([f.aLateFeeRule({ id: 'rule-1', version })])
  }))
  return loads
}

function capturePuts(respond: (attempt: number) => Response) {
  const bodies: SavedBody[] = []
  server.use(http.put('*/api/config/late-fee-rules', async ({ request }) => {
    bodies.push(await request.json() as SavedBody)
    return respond(bodies.length)
  }))
  return bodies
}

async function renderLoaded() {
  renderWithProviders(<SettingsPage />)
  await flush()
  await screen.findByRole('button', { name: 'Save Rules' })
  await flush()
  return userEvent.setup()
}

const saveRules = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: 'Save Rules' }))

describe('SettingsPage late fee rules — optimistic locking (#189)', () => {
  it('sends the version each rule was loaded with', async () => {
    serveRules([3])
    const puts = capturePuts(() => ok([f.aLateFeeRule({ id: 'rule-1', version: 4 })]))

    const user = await renderLoaded()
    await saveRules(user)

    await waitFor(() => expect(puts).toHaveLength(1))
    expect(puts[0].rules[0]).toMatchObject({ id: 'rule-1', version: 3 })
  })

  it('a second consecutive save uses the versions returned by the first', async () => {
    serveRules([3])
    const puts = capturePuts(attempt => ok([f.aLateFeeRule({ id: 'rule-1', version: 3 + attempt })]))

    const user = await renderLoaded()
    await saveRules(user)
    await waitFor(() => expect(puts).toHaveLength(1))
    await flush()
    await saveRules(user)

    await waitFor(() => expect(puts).toHaveLength(2))
    expect(puts[0].rules[0].version).toBe(3)
    expect(puts[1].rules[0].version).toBe(4)
  })

  it('on a stale save warns that unsaved changes will be discarded and offers Reload', async () => {
    serveRules([3])
    capturePuts(() => HttpResponse.json({ success: false, data: null, error: STALE_VERSION_MESSAGE }, { status: 409 }))

    const user = await renderLoaded()
    await saveRules(user)

    expect(await screen.findByText('Someone else changed the late fee rules.')).toBeInTheDocument()
    expect(screen.getByText(/discards the changes you made here/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
  })

  it('after Reload the rules show the latest versions and the next save sends them', async () => {
    serveRules([3, 5])
    const puts = capturePuts(attempt => (attempt === 1
      ? HttpResponse.json({ success: false, data: null, error: STALE_VERSION_MESSAGE }, { status: 409 })
      : ok([f.aLateFeeRule({ id: 'rule-1', version: 6 })])))

    const user = await renderLoaded()
    await saveRules(user)
    await user.click(await screen.findByRole('button', { name: 'Reload' }))
    await waitFor(() => expect(screen.queryByText('Someone else changed the late fee rules.')).not.toBeInTheDocument())
    await saveRules(user)

    await waitFor(() => expect(puts).toHaveLength(2))
    expect(puts[0].rules[0].version).toBe(3)
    expect(puts[1].rules[0].version).toBe(5)
  })

  it('shows an ordinary validation error without the Reload prompt for a non-stale failure', async () => {
    serveRules([3])
    capturePuts(() => HttpResponse.json(
      { success: false, data: null, error: 'Late fee rule ranges must not overlap.' },
      { status: 400 },
    ))

    const user = await renderLoaded()
    await saveRules(user)

    expect(await screen.findByText('Late fee rule ranges must not overlap.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reload' })).not.toBeInTheDocument()
  })
})
