import type { DevToolFact } from './dev-tools-types'

export interface JwtClaimView {
  name: string
  rawValue: string
  resolvedValue: string | null
}

export interface JwtInspectionResult {
  ok: boolean
  error: string | null
  warnings: string[]
  facts: DevToolFact[]
  header: Record<string, unknown> | null
  payload: Record<string, unknown> | null
  headerPretty: string
  payloadPretty: string
  signature: string
  bearerHeader: string
  claims: JwtClaimView[]
}

const TIME_CLAIMS = new Set(['exp', 'iat', 'nbf'])

export function inspectJwt(token: string, now = Date.now()): JwtInspectionResult {
  const normalizedToken = token.trim()

  if (!normalizedToken) {
    return buildJwtError('Добавь JWT, чтобы разобрать header и payload.')
  }

  const segments = normalizedToken.split('.')
  if (segments.length === 5) {
    return buildJwtError(
      'Похоже на JWE с пятью сегментами. Текущий inspector рассчитан на обычные signed JWT/JWS.',
    )
  }
  if (segments.length < 2 || segments.length > 3) {
    return buildJwtError('JWT должен содержать 2 или 3 сегмента через точку.')
  }

  try {
    const header = decodeJwtJsonSegment(segments[0] ?? '', 'header')
    const payload = decodeJwtJsonSegment(segments[1] ?? '', 'payload')
    const signature = segments[2] ?? ''
    const warnings = buildJwtWarnings(header, payload, signature, now)

    return {
      ok: true,
      error: null,
      warnings,
      facts: buildJwtFacts(header, payload, signature, segments.length),
      header,
      payload,
      headerPretty: JSON.stringify(header, null, 2),
      payloadPretty: JSON.stringify(payload, null, 2),
      signature,
      bearerHeader: `Authorization: Bearer ${normalizedToken}`,
      claims: buildClaimView(payload),
    }
  } catch (error) {
    return buildJwtError(error instanceof Error ? error.message : 'Не удалось декодировать JWT.')
  }
}

function buildJwtWarnings(
  header: Record<string, unknown>,
  payload: Record<string, unknown>,
  signature: string,
  now: number,
): string[] {
  const warnings: string[] = [
    'Проверка не подтверждает криптографическую подпись и не удостоверяет доверие к issuer.',
  ]
  const algorithm = stringifyClaim(header.alg)

  if (!signature) {
    warnings.push('Сегмент подписи отсутствует или пустой.')
  }
  if (algorithm?.toLowerCase() === 'none') {
    warnings.push(
      'alg=none означает unsecured token и требует отдельной проверки, допустим ли он в интеграции.',
    )
  }

  const expiration = parseNumericTimeClaim(payload.exp)
  if (expiration !== null && expiration * 1000 <= now) {
    warnings.push(
      'exp уже в прошлом: токен считается истёкшим относительно текущего времени браузера.',
    )
  }

  const notBefore = parseNumericTimeClaim(payload.nbf)
  if (notBefore !== null && notBefore * 1000 > now) {
    warnings.push('nbf ещё не наступил: токен формально ещё не должен приниматься.')
  }

  const issuedAt = parseNumericTimeClaim(payload.iat)
  if (issuedAt !== null && issuedAt * 1000 - now > 300_000) {
    warnings.push('iat заметно в будущем относительно текущих часов браузера. Проверь clock skew.')
  }

  return warnings
}

function buildJwtFacts(
  header: Record<string, unknown>,
  payload: Record<string, unknown>,
  signature: string,
  segmentCount: number,
): DevToolFact[] {
  const audience = payload.aud
  const audienceValue = Array.isArray(audience)
    ? audience.map((entry) => String(entry)).join(', ')
    : stringifyClaim(audience)

  return [
    { label: 'Сегментов', value: String(segmentCount) },
    { label: 'alg', value: stringifyClaim(header.alg) ?? 'неизвестно' },
    { label: 'typ', value: stringifyClaim(header.typ) ?? 'нет' },
    { label: 'kid', value: stringifyClaim(header.kid) ?? 'нет' },
    { label: 'iss', value: stringifyClaim(payload.iss) ?? 'нет' },
    { label: 'sub', value: stringifyClaim(payload.sub) ?? 'нет' },
    { label: 'aud', value: audienceValue ?? 'нет' },
    { label: 'Подпись', value: signature ? 'Есть' : 'Нет' },
  ]
}

function buildClaimView(payload: Record<string, unknown>): JwtClaimView[] {
  return Object.entries(payload).map(([name, value]) => ({
    name,
    rawValue: formatClaimValue(value),
    resolvedValue: TIME_CLAIMS.has(name) ? resolveTimeClaim(value) : null,
  }))
}

function resolveTimeClaim(value: unknown): string | null {
  const numericValue = parseNumericTimeClaim(value)
  if (numericValue === null) {
    return null
  }

  return `${new Date(numericValue * 1000).toISOString()} UTC`
}

function parseNumericTimeClaim(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

function formatClaimValue(value: unknown): string {
  if (typeof value === 'string') {
    return value
  }

  return JSON.stringify(value)
}

function stringifyClaim(value: unknown): string | null {
  if (value === undefined || value === null) {
    return null
  }
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return JSON.stringify(value)
}

function decodeJwtJsonSegment(segment: string, label: string): Record<string, unknown> {
  try {
    const normalized = segment.replace(/-/gu, '+').replace(/_/gu, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
    const parsed = JSON.parse(new TextDecoder().decode(bytes))

    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error()
    }

    return parsed as Record<string, unknown>
  } catch {
    throw new Error(`Сегмент JWT ${label} не удалось декодировать как JSON-объект.`)
  }
}

function buildJwtError(message: string): JwtInspectionResult {
  return {
    ok: false,
    error: message,
    warnings: [],
    facts: [],
    header: null,
    payload: null,
    headerPretty: '',
    payloadPretty: '',
    signature: '',
    bearerHeader: '',
    claims: [],
  }
}
