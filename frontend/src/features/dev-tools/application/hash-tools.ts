import type { DevToolFact } from './dev-tools-types'

export interface HashValueEntry {
  id: string
  label: string
  value: string
}

export interface HashReport {
  facts: DevToolFact[]
  digests: HashValueEntry[]
  hmacDigests: HashValueEntry[]
}

const DIGEST_ALGORITHMS = [
  { id: 'sha1', label: 'SHA-1', algorithm: 'SHA-1' },
  { id: 'sha256', label: 'SHA-256', algorithm: 'SHA-256' },
  { id: 'sha384', label: 'SHA-384', algorithm: 'SHA-384' },
  { id: 'sha512', label: 'SHA-512', algorithm: 'SHA-512' },
] as const

const HMAC_ALGORITHMS = [
  { id: 'hmac-sha256', label: 'HMAC-SHA-256', hash: 'SHA-256' },
  { id: 'hmac-sha512', label: 'HMAC-SHA-512', hash: 'SHA-512' },
] as const

export async function buildHashReport(source: string | File, secret: string): Promise<HashReport> {
  const subtle = globalThis.crypto?.subtle
  if (!subtle) {
    throw new Error('Криптография браузера недоступна, поэтому локальный расчёт хэшей невозможен.')
  }

  const bytes =
    typeof source === 'string'
      ? new TextEncoder().encode(source)
      : new Uint8Array(await source.arrayBuffer())

  const digests = await Promise.all(
    DIGEST_ALGORITHMS.map(async (entry) => ({
      id: entry.id,
      label: entry.label,
      value: bufferToHex(await subtle.digest(entry.algorithm, bytes)),
    })),
  )

  const hmacDigests = secret
    ? await Promise.all(
        HMAC_ALGORITHMS.map(async (entry) => ({
          id: entry.id,
          label: entry.label,
          value: await buildHmac(bytes, secret, entry.hash),
        })),
      )
    : []

  return {
    facts:
      typeof source === 'string'
        ? [
            { label: 'Источник', value: 'Встроенный текст' },
            { label: 'Символы', value: String(source.length) },
            { label: 'Байты', value: String(bytes.byteLength) },
            { label: 'HMAC', value: secret ? 'Включён' : 'Выключен' },
          ]
        : [
            { label: 'Источник', value: source.name || 'локальный-файл' },
            { label: 'Тип', value: source.type || 'application/octet-stream' },
            { label: 'Байты', value: String(bytes.byteLength) },
            { label: 'HMAC', value: secret ? 'Включён' : 'Выключен' },
          ],
    digests,
    hmacDigests,
  }
}

async function buildHmac(
  bytes: Uint8Array<ArrayBuffer>,
  secret: string,
  hash: 'SHA-256' | 'SHA-512',
): Promise<string> {
  const subtle = globalThis.crypto?.subtle
  if (!subtle) {
    throw new Error('Криптография браузера недоступна.')
  }

  // Секрет импортируется как raw key, потому что HMAC здесь нужен только
  // для локальной сверки payload/signature flow без внешнего key storage.
  const key = await subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash },
    false,
    ['sign'],
  )

  const signature = await subtle.sign('HMAC', key, bytes)
  return bufferToHex(signature)
}

function bufferToHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer), (byte) => byte.toString(16).padStart(2, '0')).join('')
}
