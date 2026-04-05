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

  it('resolves new illustration and design sources to dedicated adapters', () => {
    expect(resolveConverterSourceFormat('layout.psd')?.sourceStrategyId).toBe('psd-raster')
    expect(resolveConverterSourceFormat('poster.ai')?.sourceStrategyId).toBe('illustration-raster')
    expect(resolveConverterSourceFormat('cover.eps')?.sourceStrategyId).toBe('illustration-raster')
  })

  it('lists registered targets for supported sources', () => {
    const targets = listConverterTargetsForSource('poster.png')

    expect(targets.map((target) => target.extension)).toEqual([
      'jpg',
      'webp',
      'avif',
      'svg',
      'ico',
      'tiff',
      'pdf',
    ])
  })

  it('resolves registered scenario pairs', () => {
    expect(resolveConverterScenario('heic', 'jpg')?.id).toBe('heic->jpg')
    expect(resolveConverterScenario('svg', 'png')?.id).toBe('svg->png')
    expect(resolveConverterScenario('tiff', 'pdf')?.id).toBe('tiff->pdf')
    expect(resolveConverterScenario('raw', 'tiff')?.id).toBe('raw->tiff')
    expect(resolveConverterScenario('png', 'svg')?.id).toBe('png->svg')
    expect(resolveConverterScenario('psd', 'webp')?.id).toBe('psd->webp')
    expect(resolveConverterScenario('ai', 'pdf')?.id).toBe('ai->pdf')
    expect(resolveConverterScenario('eps', 'png')?.id).toBe('eps->png')
    expect(resolveConverterScenario('png', 'png')).toBeNull()
  })
})
