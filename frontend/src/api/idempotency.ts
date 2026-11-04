const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key'

export function idempotencyConfig(idempotencyKey?: string) {
  return idempotencyKey ? { headers: { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey } } : undefined
}
