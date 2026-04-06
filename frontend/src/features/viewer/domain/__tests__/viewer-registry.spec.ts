import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createViewerCapabilityScopeFixture } from '../../../processing/application/__tests__/capability-matrix.fixtures'
import { resetProcessingCapabilityScopeCache } from '../../../processing/application/processing-client'
import {
  detectFileExtension,
  listViewerFormatsByFamily,
  normalizeExtension,
  resolveViewerFormat,
} from '../viewer-registry'

const originalFetch = globalThis.fetch

describe('viewer registry', () => {
  beforeEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(createViewerCapabilityScopeFixture()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as typeof fetch
  })

  afterEach(() => {
    resetProcessingCapabilityScopeCache()
    globalThis.fetch = originalFetch
  })

  it('normalizes extensions and detects them from file names', () => {
    expect(normalizeExtension('.JPEG')).toBe('jpeg')
    expect(detectFileExtension('summer.trip.avif')).toBe('avif')
  })

  it('resolves a browser-native format by mime type and file name', async () => {
    const format = await resolveViewerFormat('poster.unknown', 'image/webp')

    expect(format?.extension).toBe('webp')
    expect(format?.previewPipeline).toBe('browser-native')
  })

  it('maps server-assisted image formats including raw aliases', async () => {
    const formats = (await listViewerFormatsByFamily('image')).filter(
      (definition) => definition.previewPipeline === 'server-assisted',
    )

    expect(formats.map((definition) => definition.extension)).toEqual(['heic', 'tiff', 'raw'])

    expect((await resolveViewerFormat('shoot.NEF'))?.extension).toBe('raw')
    expect((await resolveViewerFormat('scan.tif'))?.extension).toBe('tiff')
    expect((await resolveViewerFormat('capture.heic'))?.previewPipeline).toBe('server-assisted')
  })

  it('exposes document formats as server-assisted document intelligence routes', async () => {
    const documentFormats = await listViewerFormatsByFamily('document')

    expect(documentFormats.map((definition) => definition.extension)).toEqual([
      'pdf',
      'txt',
      'md',
      'json',
      'yaml',
      'xml',
      'env',
      'csv',
      'tsv',
      'html',
      'log',
      'sql',
      'rtf',
      'doc',
      'docx',
      'odt',
      'xls',
      'xlsx',
      'pptx',
      'epub',
      'db',
      'sqlite',
    ])

    expect((await resolveViewerFormat('sheet.csv'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('guide.markdown'))?.extension).toBe('md')
    expect((await resolveViewerFormat('payload.json'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('docker-compose.yml'))?.extension).toBe('yaml')
    expect((await resolveViewerFormat('feed.xml'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('service.env'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('table.tsv'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('index.htm'))?.extension).toBe('html')
    expect((await resolveViewerFormat('tail.log'))?.extension).toBe('log')
    expect((await resolveViewerFormat('schema.sql'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('legacy.doc'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('open.odt'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('proposal.docx'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('sheet.xls'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('deck.pptx'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('report.pdf'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('model.xlsx'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('book.epub'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('storage.sqlite'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('storage.db'))?.previewPipeline).toBe('server-assisted')
  })

  it('exposes media formats with native and server-assisted playback paths', async () => {
    const mediaFormats = await listViewerFormatsByFamily('media')

    expect(mediaFormats.map((definition) => definition.extension)).toEqual([
      'mp4',
      'mov',
      'webm',
      'avi',
      'mkv',
      'wmv',
      'flv',
    ])

    expect((await resolveViewerFormat('clip.mp4'))?.previewStrategyId).toBe('native-video')
    expect((await resolveViewerFormat('clip.mov'))?.previewPipeline).toBe('browser-native')
    expect((await resolveViewerFormat('clip.mkv'))?.previewStrategyId).toBe('server-viewer')
    expect((await resolveViewerFormat('clip.avi'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('clip.wmv'))?.previewPipeline).toBe('server-assisted')
    expect((await resolveViewerFormat('track.flac'))?.previewPipeline).toBe('server-assisted')
  })
})
