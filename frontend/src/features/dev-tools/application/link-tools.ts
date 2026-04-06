import type { DevToolFact } from './dev-tools-types'

export interface LinkToolOptions {
  stripTracking: boolean
  removeFragment: boolean
  sortParams: boolean
}

export interface LinkQueryEntry {
  key: string
  value: string
  status: 'kept' | 'removed'
}

export interface LinkAnalysisResult {
  ok: boolean
  error: string | null
  warnings: string[]
  facts: DevToolFact[]
  normalizedUrl: string
  cleanedUrl: string
  compactDelta: number
  protocol: string
  host: string
  pathname: string
  hash: string
  queryEntries: LinkQueryEntry[]
}

const TRACKING_PARAMS = new Set([
  'fbclid',
  'gclid',
  'gclsrc',
  'gbraid',
  'wbraid',
  'dclid',
  'igshid',
  'mc_cid',
  'mc_eid',
  'mkt_tok',
  'ref_src',
  'srsltid',
  'yclid',
  'mibextid',
])

export function analyzeLink(input: string, options: LinkToolOptions): LinkAnalysisResult {
  const normalizedInput = input.trim()
  if (!normalizedInput) {
    return buildLinkError('Вставь URL, чтобы разобрать и очистить ссылку.')
  }

  const warnings: string[] = []
  const preparedInput = prepareUrlInput(normalizedInput, warnings)

  try {
    const url = new URL(preparedInput)
    const cleanedUrl = new URL(url.toString())
    const removedKeys = new Set<string>()

    if (options.stripTracking) {
      for (const key of cleanedUrl.searchParams.keys()) {
        if (shouldStripTracking(key)) {
          cleanedUrl.searchParams.delete(key)
          removedKeys.add(key)
        }
      }
    }

    if (options.sortParams && cleanedUrl.search) {
      const entries = [...cleanedUrl.searchParams.entries()].sort(([left], [right]) =>
        left.localeCompare(right),
      )
      cleanedUrl.search = ''
      for (const [key, value] of entries) {
        cleanedUrl.searchParams.append(key, value)
      }
    }

    if (options.removeFragment) {
      cleanedUrl.hash = ''
    }

    const normalizedUrl = url.toString()
    const compact = cleanedUrl.toString()

    return {
      ok: true,
      error: null,
      warnings,
      facts: [
        { label: 'Protocol', value: url.protocol.replace(/:$/u, '') || 'n/a' },
        { label: 'Host', value: url.host || 'n/a' },
        { label: 'Path', value: url.pathname || '/' },
        { label: 'Params', value: String([...url.searchParams.keys()].length) },
        { label: 'Removed', value: String(removedKeys.size) },
        { label: 'Saved chars', value: String(Math.max(normalizedUrl.length - compact.length, 0)) },
      ],
      normalizedUrl,
      cleanedUrl: compact,
      compactDelta: normalizedUrl.length - compact.length,
      protocol: url.protocol.replace(/:$/u, ''),
      host: url.host,
      pathname: url.pathname,
      hash: url.hash.replace(/^#/u, ''),
      queryEntries: buildQueryEntries(url, removedKeys),
    }
  } catch {
    return buildLinkError(
      'Ссылка не распозналась как валидный URL. Проверь схему, host и спецсимволы.',
    )
  }
}

function buildQueryEntries(url: URL, removedKeys: Set<string>): LinkQueryEntry[] {
  const entries = [...url.searchParams.entries()]

  return entries.map(([key, value]) => ({
    key,
    value,
    status: removedKeys.has(key) ? 'removed' : 'kept',
  }))
}

function shouldStripTracking(key: string): boolean {
  const normalized = key.trim().toLowerCase()
  return normalized.startsWith('utm_') || TRACKING_PARAMS.has(normalized)
}

function prepareUrlInput(input: string, warnings: string[]): string {
  if (/^[a-z][a-z0-9+.-]*:/iu.test(input)) {
    return input
  }

  warnings.push('У входа не было схемы, поэтому для разбора автоматически добавлен https://')
  return `https://${input}`
}

function buildLinkError(message: string): LinkAnalysisResult {
  return {
    ok: false,
    error: message,
    warnings: [],
    facts: [],
    normalizedUrl: '',
    cleanedUrl: '',
    compactDelta: 0,
    protocol: '',
    host: '',
    pathname: '',
    hash: '',
    queryEntries: [],
  }
}
