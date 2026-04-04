import { describe, expect, it } from 'vitest'
import {
  listConverterTargetsForSource,
  resolveConverterScenario,
  resolveConverterSourceFormat,
} from '../converter-registry'

describe('converter registry', () => {
  it('resolves aliased heavy formats to their canonical source definition', () => {
    const source = resolveConverterSourceFormat('capture.nef')

    expect(source?.extension).toBe('raw')
    expect(source?.sourceStrategyId).toBe('raw-raster')
  })

  it('lists browser-first targets for supported sources', () => {
    const targets = listConverterTargetsForSource('poster.png')

    expect(targets.map((target) => target.extension)).toEqual(['jpg', 'webp'])
  })

  it('resolves registered scenario pairs', () => {
    expect(resolveConverterScenario('heic', 'jpg')?.id).toBe('heic->jpg')
    expect(resolveConverterScenario('svg', 'png')?.id).toBe('svg->png')
    expect(resolveConverterScenario('png', 'png')).toBeNull()
  })
})
