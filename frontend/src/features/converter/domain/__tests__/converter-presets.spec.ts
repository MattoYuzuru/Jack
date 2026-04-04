import { describe, expect, it } from 'vitest'
import { listConverterPresets, resolveConverterPreset } from '../converter-presets'

describe('converter presets', () => {
  it('exposes the stable preset catalogue in the intended order', () => {
    expect(listConverterPresets().map((preset) => preset.id)).toEqual([
      'original',
      'web-balanced',
      'email-attachment',
      'thumbnail',
    ])
  })

  it('falls back to original when preset is unknown', () => {
    expect(resolveConverterPreset('missing').id).toBe('original')
    expect(resolveConverterPreset('web-balanced').id).toBe('web-balanced')
  })
})
