import type { DevToolFact } from './dev-tools-types'

export type EncodingStrategyId = 'base64' | 'base64url' | 'url' | 'html' | 'unicode'
export type EncodingMode = 'encode' | 'decode'

export interface EncodingResult {
  ok: boolean
  output: string
  error: string | null
  warnings: string[]
  facts: DevToolFact[]
}

const STRATEGY_LABELS: Record<EncodingStrategyId, string> = {
  base64: 'Base64',
  base64url: 'Base64URL',
  url: 'URL component',
  html: 'HTML entities',
  unicode: 'Unicode escapes',
}

const HTML_ENTITY_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

const SIMPLE_ESCAPE_MAP: Record<string, string> = {
  '\\n': '\n',
  '\\r': '\r',
  '\\t': '\t',
  '\\\\': '\\',
  '\\"': '"',
  "\\'": "'",
  '\\b': '\b',
  '\\f': '\f',
  '\\v': '\v',
}

export function runEncodingTool(
  input: string,
  strategyId: EncodingStrategyId,
  mode: EncodingMode,
): EncodingResult {
  try {
    const warnings: string[] = []
    const output =
      mode === 'encode'
        ? encodeByStrategy(input, strategyId)
        : decodeByStrategy(input, strategyId, warnings)

    return {
      ok: true,
      output,
      error: null,
      warnings,
      facts: buildFacts(input, output, strategyId, mode),
    }
  } catch (error) {
    return {
      ok: false,
      output: '',
      error:
        error instanceof Error
          ? error.message
          : 'Не удалось выполнить выбранное encoding/deconding действие.',
      warnings: [],
      facts: buildFacts(input, '', strategyId, mode),
    }
  }
}

function buildFacts(
  input: string,
  output: string,
  strategyId: EncodingStrategyId,
  mode: EncodingMode,
): DevToolFact[] {
  return [
    { label: 'Стратегия', value: STRATEGY_LABELS[strategyId] },
    { label: 'Режим', value: mode === 'encode' ? 'Encode' : 'Decode' },
    { label: 'Input chars', value: String(input.length) },
    { label: 'Output chars', value: String(output.length) },
    { label: 'Input bytes', value: String(new TextEncoder().encode(input).length) },
    { label: 'Output bytes', value: String(new TextEncoder().encode(output).length) },
  ]
}

function encodeByStrategy(input: string, strategyId: EncodingStrategyId): string {
  switch (strategyId) {
    case 'base64':
      return encodeBase64Utf8(input)
    case 'base64url':
      return encodeBase64Utf8(input).replace(/\+/gu, '-').replace(/\//gu, '_').replace(/=+$/u, '')
    case 'url':
      return encodeURIComponent(input)
    case 'html':
      return input.replace(/[&<>"']/gu, (character) => HTML_ENTITY_MAP[character] ?? character)
    case 'unicode':
      return encodeUnicodeEscapes(input)
  }
}

function decodeByStrategy(
  input: string,
  strategyId: EncodingStrategyId,
  warnings: string[],
): string {
  switch (strategyId) {
    case 'base64':
      return decodeBase64Utf8(input, false, warnings)
    case 'base64url':
      return decodeBase64Utf8(input, true, warnings)
    case 'url':
      return decodeURIComponent(input.replace(/\+/gu, '%20'))
    case 'html':
      return decodeHtmlEntities(input)
    case 'unicode':
      return decodeUnicodeEscapes(input)
  }
}

function encodeBase64Utf8(input: string): string {
  const bytes = new TextEncoder().encode(input)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

function decodeBase64Utf8(input: string, base64Url: boolean, warnings: string[]): string {
  const normalizedWhitespace = input.replace(/\s+/gu, '')
  let normalized = base64Url
    ? normalizedWhitespace.replace(/-/gu, '+').replace(/_/gu, '/')
    : normalizedWhitespace

  const requiredPadding = normalized.length % 4
  if (requiredPadding !== 0) {
    normalized = normalized.padEnd(normalized.length + (4 - requiredPadding), '=')
    warnings.push('К входу добавлен padding, потому что строка пришла без завершающих =.')
  }

  const decodedBinary = atob(normalized)
  const bytes = Uint8Array.from(decodedBinary, (character) => character.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

function decodeHtmlEntities(input: string): string {
  const textarea = document.createElement('textarea')
  textarea.innerHTML = input
  return textarea.value
}

function encodeUnicodeEscapes(input: string): string {
  let result = ''

  for (const character of input) {
    const codePoint = character.codePointAt(0) ?? 0

    if (character === '\n') {
      result += '\\n'
      continue
    }
    if (character === '\r') {
      result += '\\r'
      continue
    }
    if (character === '\t') {
      result += '\\t'
      continue
    }
    if (character === '\\') {
      result += '\\\\'
      continue
    }
    if (codePoint >= 32 && codePoint <= 126) {
      result += character
      continue
    }

    result +=
      codePoint > 0xffff
        ? `\\u{${codePoint.toString(16).toUpperCase()}}`
        : `\\u${codePoint.toString(16).toUpperCase().padStart(4, '0')}`
  }

  return result
}

function decodeUnicodeEscapes(input: string): string {
  // Здесь intentionally держим явный decode порядок, чтобы не разбирать
  // последовательности повторно и не съесть обычные обратные слэши раньше времени.
  return input
    .replace(/\\u\{([0-9a-fA-F]{1,6})\}/gu, (_, codePoint) =>
      String.fromCodePoint(Number.parseInt(codePoint, 16)),
    )
    .replace(/\\u([0-9a-fA-F]{4})/gu, (_, codePoint) =>
      String.fromCharCode(Number.parseInt(codePoint, 16)),
    )
    .replace(/\\x([0-9a-fA-F]{2})/gu, (_, codePoint) =>
      String.fromCharCode(Number.parseInt(codePoint, 16)),
    )
    .replace(/\\[nrt\\'"bfv]/gu, (sequence) => SIMPLE_ESCAPE_MAP[sequence] ?? sequence)
}
