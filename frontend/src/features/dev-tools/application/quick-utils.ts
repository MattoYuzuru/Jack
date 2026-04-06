import type { DevToolFact } from './dev-tools-types'

const CROCKFORD_BASE32 = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

export interface TimestampAnalysisResult {
  ok: boolean
  error: string | null
  facts: DevToolFact[]
  isoUtc: string
  localTime: string
  epochSeconds: string
  epochMilliseconds: string
}

export interface BasicAuthResult {
  header: string
  encoded: string
  curlSnippet: string
}

export function generateUuid(): string {
  return globalThis.crypto?.randomUUID?.() ?? createUuidFallback()
}

export function generateUlid(timestamp = Date.now(), randomBytes = defaultRandomBytes(10)): string {
  const timePart = encodeTimePart(timestamp)
  const randomPart = encodeRandomPart(randomBytes)
  return `${timePart}${randomPart}`
}

export function analyzeTimestamp(input: string): TimestampAnalysisResult {
  const normalizedInput = input.trim()
  if (!normalizedInput) {
    return buildTimestampError('Добавь Unix timestamp или ISO date string.')
  }

  const parsedDate = parseTimestampInput(normalizedInput)
  if (!parsedDate) {
    return buildTimestampError(
      'Не удалось распознать timestamp. Используй epoch seconds, epoch milliseconds или ISO date.',
    )
  }

  return {
    ok: true,
    error: null,
    facts: [
      {
        label: 'Source kind',
        value: /^\d+$/u.test(normalizedInput) ? 'Numeric epoch' : 'Date string',
      },
      { label: 'Timezone', value: 'Browser local + UTC' },
    ],
    isoUtc: parsedDate.toISOString(),
    localTime: parsedDate.toLocaleString(),
    epochSeconds: String(Math.floor(parsedDate.getTime() / 1000)),
    epochMilliseconds: String(parsedDate.getTime()),
  }
}

export function buildBasicAuthHeader(username: string, password: string): BasicAuthResult {
  const encoded = encodeBase64Utf8(`${username}:${password}`)
  return {
    header: `Authorization: Basic ${encoded}`,
    encoded,
    curlSnippet: `curl -H "Authorization: Basic ${encoded}" https://example.test`,
  }
}

function parseTimestampInput(input: string): Date | null {
  if (/^-?\d+$/u.test(input)) {
    const numericValue = Number(input)
    if (!Number.isFinite(numericValue)) {
      return null
    }

    const milliseconds =
      Math.abs(numericValue) < 100_000_000_000 ? numericValue * 1000 : numericValue
    const date = new Date(milliseconds)
    return Number.isNaN(date.getTime()) ? null : date
  }

  const date = new Date(input)
  return Number.isNaN(date.getTime()) ? null : date
}

function encodeTimePart(timestamp: number): string {
  let value = Math.max(0, Math.floor(timestamp))
  let encoded = ''

  for (let index = 0; index < 10; index += 1) {
    encoded = CROCKFORD_BASE32[value % 32] + encoded
    value = Math.floor(value / 32)
  }

  return encoded
}

function encodeRandomPart(randomBytes: Uint8Array): string {
  let encoded = ''
  let buffer = 0
  let bits = 0

  for (const byte of randomBytes) {
    buffer = (buffer << 8) | byte
    bits += 8

    while (bits >= 5) {
      encoded += CROCKFORD_BASE32[(buffer >>> (bits - 5)) & 31]
      bits -= 5
    }
  }

  if (bits > 0) {
    encoded += CROCKFORD_BASE32[(buffer << (5 - bits)) & 31]
  }

  return encoded.slice(0, 16)
}

function defaultRandomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length)
  globalThis.crypto?.getRandomValues(bytes)
  return bytes
}

function encodeBase64Utf8(input: string): string {
  const bytes = new TextEncoder().encode(input)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

function createUuidFallback(): string {
  const bytes = defaultRandomBytes(16)
  const versionByte = bytes[6] ?? 0
  const variantByte = bytes[8] ?? 0

  bytes[6] = (versionByte & 0x0f) | 0x40
  bytes[8] = (variantByte & 0x3f) | 0x80

  return [...bytes]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
    .replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/u, '$1-$2-$3-$4-$5')
}

function buildTimestampError(message: string): TimestampAnalysisResult {
  return {
    ok: false,
    error: message,
    facts: [],
    isoUtc: '',
    localTime: '',
    epochSeconds: '',
    epochMilliseconds: '',
  }
}
