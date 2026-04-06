import { describe, expect, it } from 'vitest'
import { runEncodingTool } from '../encoding-tools'

describe('encoding tools', () => {
  it('encodes and decodes utf-8 through base64', () => {
    const encoded = runEncodingTool('Привет, Jack', 'base64', 'encode')
    const decoded = runEncodingTool(encoded.output, 'base64', 'decode')

    expect(encoded.ok).toBe(true)
    expect(decoded.ok).toBe(true)
    expect(decoded.output).toBe('Привет, Jack')
  })

  it('decodes html entities back into readable text', () => {
    const result = runEncodingTool('&lt;div&gt;Jack &amp; Jill&lt;/div&gt;', 'html', 'decode')

    expect(result.ok).toBe(true)
    expect(result.output).toBe('<div>Jack & Jill</div>')
  })

  it('supports unicode escape roundtrip', () => {
    const encoded = runEncodingTool('Jack 🚀', 'unicode', 'encode')
    const decoded = runEncodingTool(encoded.output, 'unicode', 'decode')

    expect(encoded.output).toContain('\\u{1F680}')
    expect(decoded.output).toBe('Jack 🚀')
  })
})
