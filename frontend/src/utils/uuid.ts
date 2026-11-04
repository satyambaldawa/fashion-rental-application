const RANDOM_BYTE_COUNT = 16
const VERSION_BYTE_INDEX = 6
const VARIANT_BYTE_INDEX = 8
const VERSION_4_BITS = 0x40
const VARIANT_RFC4122_BITS = 0x80

// crypto.randomUUID only exists in a secure context (HTTPS or localhost) and the shop tablet may
// be served over plain HTTP. crypto.getRandomValues has no such restriction, so fall back to
// building an RFC 4122 v4 UUID from it — the server's UUID header parser accepts either.
export function generateUuidV4(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  const bytes = crypto.getRandomValues(new Uint8Array(RANDOM_BYTE_COUNT))
  bytes[VERSION_BYTE_INDEX] = (bytes[VERSION_BYTE_INDEX] & 0x0f) | VERSION_4_BITS
  bytes[VARIANT_BYTE_INDEX] = (bytes[VARIANT_BYTE_INDEX] & 0x3f) | VARIANT_RFC4122_BITS

  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0'))
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-')
}
