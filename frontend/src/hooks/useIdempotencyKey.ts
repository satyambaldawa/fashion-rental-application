import { useCallback, useRef, useState } from 'react'
import { generateUuidV4 } from '../utils/uuid'

interface IssuedKey {
  serializedRequest: string
  key: string
}

/**
 * One idempotency key per distinct outgoing request. Retrying the same request (a lost response,
 * a network error) reuses the key so the server replays instead of creating a duplicate; any
 * change to the request, a successful submit (call rotate), or a remount yields a fresh key so a
 * deliberate repeat of an identical request is never mistaken for a retry.
 */
export function useIdempotencyKey(request: unknown): { key: string; rotate: () => void } {
  const serializedRequest = JSON.stringify(request) ?? ''
  const issued = useRef<IssuedKey | null>(null)
  const [, setRotations] = useState(0)

  // eslint-disable-next-line react-hooks/refs -- the ref deliberately memoises one key per serialized request across renders so a retry reuses it; re-running this in a StrictMode double render is idempotent
  if (issued.current === null || issued.current.serializedRequest !== serializedRequest) {
    issued.current = { serializedRequest, key: generateUuidV4() }
  }

  const rotate = useCallback(() => {
    issued.current = null
    setRotations(count => count + 1)
  }, [])

  // eslint-disable-next-line react-hooks/refs -- reads the key memoised above for this serialized request; stable across re-renders until the request changes or rotate is called
  return { key: issued.current.key, rotate }
}
