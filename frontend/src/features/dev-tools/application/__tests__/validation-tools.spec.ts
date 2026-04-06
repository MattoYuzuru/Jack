import { describe, expect, it } from 'vitest'
import { validateStructuredText } from '../validation-tools'

describe('validation tools', () => {
  it('normalizes valid json', () => {
    const result = validateStructuredText('{"jack":true,"iteration":7}', 'json')

    expect(result.valid).toBe(true)
    expect(result.normalized).toContain('"iteration": 7')
    expect(result.facts.find((fact) => fact.label === 'Root type')?.value).toBe('object')
  })

  it('returns yaml diagnostics for malformed input', () => {
    const result = validateStructuredText('jack:\n  - valid\n broken', 'yaml')

    expect(result.valid).toBe(false)
    expect(result.issues[0]?.code).toBe('YAML_PARSE_ERROR')
  })

  it('keeps env duplicates as warnings but not hard errors', () => {
    const result = validateStructuredText(
      'API_URL=https://jack.local\nAPI_URL=https://override',
      'env',
    )

    expect(result.valid).toBe(true)
    expect(result.issues.find((issue) => issue.code === 'ENV_DUPLICATE_KEY')?.severity).toBe(
      'warning',
    )
  })
})
