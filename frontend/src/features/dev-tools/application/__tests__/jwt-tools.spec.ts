import { describe, expect, it } from 'vitest'
import { inspectJwt } from '../jwt-tools'

function encodeBase64Url(value: string): string {
  return Buffer.from(value, 'utf8')
    .toString('base64')
    .replace(/\+/gu, '-')
    .replace(/\//gu, '_')
    .replace(/=+$/u, '')
}

describe('jwt tools', () => {
  it('decodes header and payload and exposes claim facts', () => {
    const token = [
      encodeBase64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT', kid: 'dev-key' })),
      encodeBase64Url(
        JSON.stringify({
          iss: 'jack.local',
          sub: 'developer',
          aud: 'dev-tools',
          exp: 4_102_444_800,
        }),
      ),
      'signature',
    ].join('.')

    const result = inspectJwt(token, Date.UTC(2026, 3, 6))

    expect(result.ok).toBe(true)
    expect(result.header?.alg).toBe('HS256')
    expect(result.payload?.sub).toBe('developer')
    expect(result.bearerHeader).toContain('Authorization: Bearer')
    expect(result.claims.find((claim) => claim.name === 'exp')?.resolvedValue).toContain('UTC')
  })

  it('warns about unsecured tokens', () => {
    const token = [
      encodeBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' })),
      encodeBase64Url(JSON.stringify({ sub: 'developer' })),
      '',
    ].join('.')

    const result = inspectJwt(token)

    expect(result.ok).toBe(true)
    expect(result.warnings.join(' ')).toContain('alg=none')
    expect(result.warnings.join(' ')).toContain('Signature segment отсутствует')
  })
})
