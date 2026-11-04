// Must match StaleUpdateException.MESSAGE on the backend — a stale-save 409 is recognised by this
// exact text, so an unrelated 409 (e.g. duplicate phone) never offers a Reload.
export const STALE_VERSION_MESSAGE =
  'This record was changed by someone else since you opened it. Reload to see the latest version.'

interface ApiErrorShape {
  response?: { status?: number; data?: { error?: string } }
}

export function isStaleVersionError(err: unknown): boolean {
  const response = (err as ApiErrorShape | null)?.response
  return response?.status === 409 && response.data?.error === STALE_VERSION_MESSAGE
}

// A PWA bundle cached from before versions existed submits no version; the server rejects it
// with a field error naming `version`.
export function isMissingVersionError(err: unknown): boolean {
  const response = (err as ApiErrorShape | null)?.response
  return response?.status === 400 && (response.data?.error ?? '').includes('version')
}
