import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPdfToolkitCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import {
  detectPdfToolkitExtension,
  getPdfToolkitOperations,
  normalizePdfToolkitExtension,
  resolvePdfToolkitDirectSource,
  resolvePdfToolkitImportSource,
} from '../pdf-toolkit-registry'

const originalFetch = globalThis.fetch

describe('pdf toolkit registry', () => {
  beforeEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createPdfToolkitCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch
  })

  afterEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = originalFetch
  })

  it('normalizes pdf-toolkit extensions and resolves direct pdf sources', async () => {
    expect(normalizePdfToolkitExtension('.PDF')).toBe('pdf')
    expect(detectPdfToolkitExtension('contract.final.PDF')).toBe('pdf')

    const directSource = await resolvePdfToolkitDirectSource('contract.pdf', 'application/pdf')
    expect(directSource?.routeKind).toBe('direct-pdf')
  })

  it('resolves import-to-pdf sources and exposes operation metadata', async () => {
    const importSource = await resolvePdfToolkitImportSource('poster.jpeg', 'image/jpeg')
    const operations = await getPdfToolkitOperations()

    expect(importSource?.routeKind).toBe('convert-to-pdf')
    expect(importSource?.routeLabel).toContain('PDF')
    expect(operations.map((operation) => operation.id)).toEqual([
      'merge',
      'split',
      'rotate',
      'reorder',
      'ocr',
      'sign',
      'redact',
      'protect',
      'unlock',
    ])
  })
})
