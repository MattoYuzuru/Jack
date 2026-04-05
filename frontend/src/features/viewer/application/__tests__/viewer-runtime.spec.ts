import { afterEach, describe, expect, it, vi } from 'vitest'
import { createEmptyMetadataPayload } from '../viewer-metadata'
import { createViewerRuntime, releaseViewerEntry } from '../viewer-runtime'
import { ViewerDatabaseFormatError } from '../viewer-document-database'

const originalCreateObjectUrl = URL.createObjectURL
const originalRevokeObjectUrl = URL.revokeObjectURL

afterEach(() => {
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: originalCreateObjectUrl,
  })

  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: originalRevokeObjectUrl,
  })
})

describe('viewer runtime', () => {
  it('builds a native image preview for browser-supported formats', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:preview'),
    })

    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 1440, height: 900 }),
      loadNativeMetadata: async () => ({
        ...createEmptyMetadataPayload(),
        summary: [{ label: 'Камера', value: 'JackCam' }],
      }),
    })

    const result = await runtime.resolve(new File(['image'], 'poster.png', { type: 'image/png' }))

    if (result.kind !== 'image') {
      throw new Error('Expected a native image preview result.')
    }

    expect(result.objectUrl).toBe('blob:preview')
    expect(result.dimensions).toEqual({ width: 1440, height: 900 })
    expect(result.format.extension).toBe('png')
    expect(result.metadata.summary).toEqual([{ label: 'Камера', value: 'JackCam' }])
  })

  it('builds a decoded preview for heic files', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:heic-preview'),
    })

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 3024, height: 4032 }),
      decodeHeicImage: async () => ({
        bytes: new Uint8Array([1, 2, 3]),
        mimeType: 'image/jpeg',
        metadata: {
          ...createEmptyMetadataPayload(),
          summary: [{ label: 'Тип файла', value: 'HEIC' }],
        },
        previewLabel: 'HEIC decode adapter',
      }),
    })

    const result = await runtime.resolve(
      new File(['image'], 'capture.heic', { type: 'image/heic' }),
    )

    if (result.kind !== 'image') {
      throw new Error('Expected a decoded HEIC preview result.')
    }

    expect(result.format.extension).toBe('heic')
    expect(result.previewLabel).toBe('HEIC decode adapter')
    expect(result.metadata.summary).toEqual([{ label: 'Тип файла', value: 'HEIC' }])
  })

  it('routes raw aliases through the raw preview adapter', async () => {
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:raw-preview'),
    })

    const runtime = createViewerRuntime({
      inspectNativeImage: async () => ({ width: 1600, height: 1067 }),
      decodeRawImage: async () => ({
        bytes: new Uint8Array([4, 5, 6]),
        mimeType: 'image/png',
        metadata: {
          ...createEmptyMetadataPayload(),
          summary: [{ label: 'Камера', value: 'RAW Body' }],
        },
        previewLabel: 'RAW preview extraction',
      }),
    })

    const result = await runtime.resolve(new File(['raw'], 'session.nef'))

    if (result.kind !== 'image') {
      throw new Error('Expected a RAW preview result.')
    }

    expect(result.format.extension).toBe('raw')
    expect(result.previewLabel).toBe('RAW preview extraction')
    expect(result.metadata.summary).toEqual([{ label: 'Камера', value: 'RAW Body' }])
  })

  it('builds a document preview for pdf files', async () => {
    const revokeObjectUrl = vi.fn()

    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl,
    })

    const runtime = createViewerRuntime({
      buildPdfDocument: async () => ({
        summary: [{ label: 'Страниц', value: '3' }],
        searchableText: 'Alpha beta gamma',
        warnings: ['Search layer ограничен первыми страницами.'],
        layout: {
          mode: 'pdf',
          objectUrl: 'blob:pdf-preview',
          pageCount: 3,
        },
        previewLabel: 'PDF browser preview',
      }),
    })

    const result = await runtime.resolve(new File(['pdf'], 'deck.pdf', { type: 'application/pdf' }))

    if (result.kind !== 'document') {
      throw new Error('Expected a document preview result.')
    }

    expect(result.layout.mode).toBe('pdf')
    expect(result.previewLabel).toBe('PDF browser preview')
    expect(result.summary).toEqual([{ label: 'Страниц', value: '3' }])
    expect(result.searchableText).toContain('beta')

    releaseViewerEntry(result)

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:pdf-preview')
  })

  it('builds a video preview for browser-native formats', async () => {
    const runtime = createViewerRuntime({
      buildNativeVideo: async () => ({
        summary: [{ label: 'Длительность', value: '00:12' }],
        warnings: [],
        layout: {
          mode: 'native',
          objectUrl: 'blob:video-preview',
          durationSeconds: 12,
          width: 1920,
          height: 1080,
        },
        previewLabel: 'Browser video',
      }),
    })

    const result = await runtime.resolve(new File(['video'], 'clip.mp4', { type: 'video/mp4' }))

    if (result.kind !== 'video') {
      throw new Error('Expected a video preview result.')
    }

    expect(result.previewLabel).toBe('Browser video')
    expect(result.layout.durationSeconds).toBe(12)
    expect(result.layout.width).toBe(1920)
  })

  it('routes xlsx files through the workbook document adapter', async () => {
    const runtime = createViewerRuntime({
      buildXlsxDocument: async () => ({
        summary: [{ label: 'Sheets', value: '2' }],
        searchableText: 'Summary Viewer',
        warnings: [],
        layout: {
          mode: 'workbook',
          text: 'Summary Viewer',
          activeSheetIndex: 0,
          sheets: [
            {
              id: 'sheet-1',
              name: 'Summary',
              table: {
                columns: ['Name'],
                rows: [['Viewer']],
                totalRows: 1,
                totalColumns: 1,
                delimiter: '',
              },
            },
          ],
        },
        previewLabel: 'XLSX workbook adapter',
      }),
    })

    const result = await runtime.resolve(
      new File(['xlsx'], 'report.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }),
    )

    if (result.kind !== 'document') {
      throw new Error('Expected an XLSX document preview result.')
    }

    expect(result.previewLabel).toBe('XLSX workbook adapter')
    expect(result.layout.mode).toBe('workbook')
  })

  it('routes doc files through the legacy document adapter', async () => {
    const runtime = createViewerRuntime({
      buildDocDocument: async () => ({
        summary: [{ label: 'Тип документа', value: 'DOC' }],
        searchableText: 'Legacy viewer text',
        warnings: [],
        layout: {
          mode: 'text',
          text: 'Legacy viewer text',
          paragraphs: ['Legacy viewer text'],
        },
        previewLabel: 'DOC legacy text adapter',
      }),
    })

    const result = await runtime.resolve(new File(['doc'], 'legacy.doc', { type: 'application/msword' }))

    if (result.kind !== 'document') {
      throw new Error('Expected a DOC document preview result.')
    }

    expect(result.previewLabel).toBe('DOC legacy text adapter')
    expect(result.layout.mode).toBe('text')
  })

  it('routes epub files through the reflow document adapter', async () => {
    const runtime = createViewerRuntime({
      buildEpubDocument: async () => ({
        summary: [{ label: 'Тип документа', value: 'EPUB' }],
        searchableText: 'Viewer chapter text',
        warnings: [],
        layout: {
          mode: 'html',
          text: 'Viewer chapter text',
          srcDoc: '<html><body><h1>Viewer</h1></body></html>',
          outline: [{ id: 'epub-1', label: 'Viewer', level: 1 }],
        },
        previewLabel: 'EPUB reading adapter',
      }),
    })

    const result = await runtime.resolve(
      new File(['epub'], 'book.epub', {
        type: 'application/epub+zip',
      }),
    )

    if (result.kind !== 'document') {
      throw new Error('Expected an EPUB document preview result.')
    }

    expect(result.previewLabel).toBe('EPUB reading adapter')
    expect(result.layout.mode).toBe('html')
  })

  it('falls back to unknown when db extension does not confirm sqlite signature', async () => {
    const runtime = createViewerRuntime({
      buildSqliteDocument: async () => {
        throw new ViewerDatabaseFormatError('DB file is not SQLite')
      },
    })

    const result = await runtime.resolve(new File(['db'], 'storage.db'))

    expect(result.kind).toBe('unknown')

    if (result.kind !== 'unknown') {
      throw new Error('Expected a DB fallback result.')
    }

    expect(result.headline).toContain('DB')
    expect(result.detail).toContain('not SQLite')
  })

  it('routes planned media formats into a capability-aware placeholder', async () => {
    const runtime = createViewerRuntime()
    const result = await runtime.resolve(
      new File(['video'], 'archive.mkv', {
        type: 'video/x-matroska',
      }),
    )

    expect(result.kind).toBe('unknown')

    if (result.kind !== 'unknown') {
      throw new Error('Expected a planned media placeholder.')
    }

    expect(result.headline).toContain('MKV')
    expect(result.detail).toContain('playback path')
  })
})
