import YAML from 'yaml'
import type { DevToolFact, DevToolIssue } from './dev-tools-types'

export type ValidationFormatId = 'json' | 'yaml' | 'xml' | 'env'

export interface ValidationResult {
  valid: boolean
  normalized: string
  facts: DevToolFact[]
  issues: DevToolIssue[]
}

const ENV_KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/u

export function validateStructuredText(
  input: string,
  formatId: ValidationFormatId,
): ValidationResult {
  if (!input.trim()) {
    return {
      valid: false,
      normalized: '',
      facts: [],
      issues: [
        { severity: 'error', code: 'EMPTY_INPUT', message: 'Вставь содержимое для проверки.' },
      ],
    }
  }

  switch (formatId) {
    case 'json':
      return validateJson(input)
    case 'yaml':
      return validateYaml(input)
    case 'xml':
      return validateXml(input)
    case 'env':
      return validateEnv(input)
  }
}

function validateJson(input: string): ValidationResult {
  try {
    const parsed = JSON.parse(input)

    return {
      valid: true,
      normalized: JSON.stringify(parsed, null, 2),
      facts: [
        { label: 'Формат', value: 'JSON' },
        { label: 'Тип корня', value: resolveValueType(parsed) },
        { label: 'Ключи верхнего уровня', value: String(countTopLevelKeys(parsed)) },
      ],
      issues: [],
    }
  } catch (error) {
    return {
      valid: false,
      normalized: '',
      facts: [{ label: 'Формат', value: 'JSON' }],
      issues: [
        {
          severity: 'error',
          code: 'JSON_PARSE_ERROR',
          message: error instanceof Error ? error.message : 'JSON не распарсился.',
        },
      ],
    }
  }
}

function validateYaml(input: string): ValidationResult {
  const document = YAML.parseDocument(input, {
    prettyErrors: true,
    strict: false,
  })
  const issues: DevToolIssue[] = [
    ...document.errors.map((error) => ({
      severity: 'error' as const,
      code: 'YAML_PARSE_ERROR',
      message: error.message,
    })),
    ...document.warnings.map((warning) => ({
      severity: 'warning' as const,
      code: 'YAML_WARNING',
      message: warning.message,
    })),
  ]

  if (document.errors.length > 0) {
    return {
      valid: false,
      normalized: '',
      facts: [{ label: 'Формат', value: 'YAML' }],
      issues,
    }
  }

  const parsed = document.toJS()
  return {
    valid: true,
    normalized: YAML.stringify(parsed),
    facts: [
      { label: 'Формат', value: 'YAML' },
      { label: 'Тип корня', value: resolveValueType(parsed) },
      { label: 'Ключи верхнего уровня', value: String(countTopLevelKeys(parsed)) },
    ],
    issues,
  }
}

function validateXml(input: string): ValidationResult {
  const document = new DOMParser().parseFromString(input, 'application/xml')
  const parserError = document.querySelector('parsererror')

  if (parserError) {
    return {
      valid: false,
      normalized: '',
      facts: [{ label: 'Формат', value: 'XML' }],
      issues: [
        {
          severity: 'error',
          code: 'XML_PARSE_ERROR',
          message: parserError.textContent?.trim() || 'XML не распарсился.',
        },
      ],
    }
  }

  const rootElement = document.documentElement
  return {
    valid: true,
    normalized: formatXml(new XMLSerializer().serializeToString(document)),
    facts: [
      { label: 'Формат', value: 'XML' },
      { label: 'Корневой элемент', value: rootElement.tagName },
      { label: 'Атрибуты', value: String(rootElement.attributes.length) },
      { label: 'Дочерние узлы', value: String(rootElement.children.length) },
    ],
    issues: [],
  }
}

function validateEnv(input: string): ValidationResult {
  const lines = input.replace(/\r\n/gu, '\n').split('\n')
  const issues: DevToolIssue[] = []
  const entries: Array<[string, string]> = []
  const seenKeys = new Set<string>()

  lines.forEach((line, index) => {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) {
      return
    }

    const separatorIndex = line.indexOf('=')
    if (separatorIndex === -1) {
      issues.push({
        severity: 'error',
        code: 'ENV_MISSING_EQUALS',
        message: `Строка ${index + 1}: ожидается пара KEY=value.`,
      })
      return
    }

    const key = line.slice(0, separatorIndex).trim()
    const rawValue = line.slice(separatorIndex + 1)
    if (!ENV_KEY_PATTERN.test(key)) {
      issues.push({
        severity: 'error',
        code: 'ENV_INVALID_KEY',
        message: `Строка ${index + 1}: ${key} не выглядит как корректное имя переменной.`,
      })
      return
    }

    if (seenKeys.has(key)) {
      issues.push({
        severity: 'warning',
        code: 'ENV_DUPLICATE_KEY',
        message: `Строка ${index + 1}: ключ ${key} уже встречался выше.`,
      })
    }

    seenKeys.add(key)
    entries.push([key, normalizeEnvValue(rawValue)])
  })

  const hasErrors = issues.some((issue) => issue.severity === 'error')
  return {
    valid: !hasErrors,
    normalized: hasErrors ? '' : entries.map(([key, value]) => `${key}=${value}`).join('\n'),
    facts: [
      { label: 'Формат', value: '.env' },
      { label: 'Переменные', value: String(entries.length) },
      {
        label: 'Предупреждения',
        value: String(issues.filter((issue) => issue.severity === 'warning').length),
      },
    ],
    issues,
  }
}

function normalizeEnvValue(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }

  if (
    (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed
  }

  return trimmed.includes(' ') ? `"${trimmed.replace(/"/gu, '\\"')}"` : trimmed
}

function resolveValueType(value: unknown): string {
  if (Array.isArray(value)) {
    return 'массив'
  }
  if (value === null) {
    return 'null'
  }
  switch (typeof value) {
    case 'object':
      return 'объект'
    case 'string':
      return 'строка'
    case 'number':
      return 'число'
    case 'boolean':
      return 'булево'
    default:
      return typeof value
  }
}

function countTopLevelKeys(value: unknown): number {
  if (Array.isArray(value)) {
    return value.length
  }
  if (value && typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>).length
  }
  return 0
}

function formatXml(xml: string): string {
  const normalized = xml.replace(/>\s*</gu, '>\n<')
  let indentLevel = 0

  return normalized
    .split('\n')
    .map((line) => {
      if (line.startsWith('</')) {
        indentLevel = Math.max(indentLevel - 1, 0)
      }

      const formattedLine = `${'  '.repeat(indentLevel)}${line}`
      if (/^<[^!?/][^>]*[^/]>/u.test(line)) {
        indentLevel += 1
      }

      return formattedLine
    })
    .join('\n')
}
