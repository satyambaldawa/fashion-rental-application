export function jwtWithRole(role: string): string {
  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'user', role, exp: 9999999999 })}.signature`
}
