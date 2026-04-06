import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createConverterCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import {
  listConverterTargetsForSource,
  resolveConverterScenario,
  resolveConverterSourceFormat,
} from '../converter-registry'

const originalFetch = globalThis.fetch

describe('converter registry', () => {
  beforeEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createConverterCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch
  })

  afterEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = originalFetch
  })

  it('resolves aliased heavy formats to their canonical source definition', async () => {
    const source = await resolveConverterSourceFormat('capture.nef')

    expect(source?.extension).toBe('raw')
    expect(source?.sourceStrategyId).toBe('raw-raster')
  })

  it('resolves new illustration and design sources to dedicated adapters', async () => {
    expect((await resolveConverterSourceFormat('layout.psd'))?.sourceStrategyId).toBe('psd-raster')
    expect((await resolveConverterSourceFormat('poster.ai'))?.sourceStrategyId).toBe(
      'illustration-raster',
    )
    expect((await resolveConverterSourceFormat('cover.eps'))?.sourceStrategyId).toBe(
      'illustration-raster',
    )
    expect((await resolveConverterSourceFormat('mix.m4a'))?.sourceStrategyId).toBe('audio-media')
  })

  it('lists registered targets for supported sources', async () => {
    const targets = await listConverterTargetsForSource('poster.png')

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

  it('lists registered media targets for mp4 sources', async () => {
    const targets = await listConverterTargetsForSource('clip.mp4')

    expect(targets.map((target) => target.extension)).toEqual([
      'mp4',
      'webm',
      'gif',
      'mp3',
      'wav',
      'aac',
    ])
  })

  it('resolves registered scenario pairs', async () => {
    expect((await resolveConverterScenario('heic', 'jpg'))?.id).toBe('heic->jpg')
    expect((await resolveConverterScenario('svg', 'png'))?.id).toBe('svg->png')
    expect((await resolveConverterScenario('tiff', 'pdf'))?.id).toBe('tiff->pdf')
    expect((await resolveConverterScenario('raw', 'tiff'))?.id).toBe('raw->tiff')
    expect((await resolveConverterScenario('png', 'svg'))?.id).toBe('png->svg')
    expect((await resolveConverterScenario('psd', 'webp'))?.id).toBe('psd->webp')
    expect((await resolveConverterScenario('ai', 'pdf'))?.id).toBe('ai->pdf')
    expect((await resolveConverterScenario('eps', 'png'))?.id).toBe('eps->png')
    expect((await resolveConverterScenario('mp4', 'mp4'))?.id).toBe('mp4->mp4')
    expect((await resolveConverterScenario('m4a', 'mp3'))?.id).toBe('m4a->mp3')
    expect(await resolveConverterScenario('png', 'png')).toBeNull()
  })
})
