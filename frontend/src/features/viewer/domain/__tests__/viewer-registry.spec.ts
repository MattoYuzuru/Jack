import { describe, expect, it } from 'vitest'
import {
  detectFileExtension,
  listViewerFormatsByFamily,
  normalizeExtension,
  resolveViewerFormat,
} from '../viewer-registry'

describe('viewer registry', () => {
  it('normalizes extensions and detects them from file names', () => {
    expect(normalizeExtension('.JPEG')).toBe('jpeg')
    expect(detectFileExtension('summer.trip.avif')).toBe('avif')
  })

  it('resolves a browser-native format by mime type and file name', () => {
    const format = resolveViewerFormat('poster.unknown', 'image/webp')

    expect(format?.extension).toBe('webp')
    expect(format?.previewPipeline).toBe('browser-native')
  })

  it('maps server-assisted image formats including raw aliases', () => {
    const formats = listViewerFormatsByFamily('image').filter(
      (definition) => definition.previewPipeline === 'server-assisted',
    )

    expect(formats.map((definition) => definition.extension)).toEqual(['heic', 'tiff', 'raw'])

    expect(resolveViewerFormat('shoot.NEF')?.extension).toBe('raw')
    expect(resolveViewerFormat('scan.tif')?.extension).toBe('tiff')
    expect(resolveViewerFormat('capture.heic')?.previewPipeline).toBe('server-assisted')
  })

  it('exposes document formats as server-assisted document intelligence routes', () => {
    const documentFormats = listViewerFormatsByFamily('document')

    expect(documentFormats.map((definition) => definition.extension)).toEqual([
      'pdf',
      'txt',
      'csv',
      'html',
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

    expect(resolveViewerFormat('sheet.csv')?.previewStrategyId).toBe('csv-document')
    expect(resolveViewerFormat('index.htm')?.extension).toBe('html')
    expect(resolveViewerFormat('legacy.doc')?.previewStrategyId).toBe('doc-document')
    expect(resolveViewerFormat('open.odt')?.previewStrategyId).toBe('odt-document')
    expect(resolveViewerFormat('proposal.docx')?.previewStrategyId).toBe('docx-document')
    expect(resolveViewerFormat('sheet.xls')?.previewStrategyId).toBe('xls-document')
    expect(resolveViewerFormat('deck.pptx')?.previewStrategyId).toBe('pptx-document')
    expect(resolveViewerFormat('report.pdf')?.previewPipeline).toBe('server-assisted')
    expect(resolveViewerFormat('model.xlsx')?.previewPipeline).toBe('server-assisted')
    expect(resolveViewerFormat('book.epub')?.previewStrategyId).toBe('epub-document')
    expect(resolveViewerFormat('storage.sqlite')?.previewStrategyId).toBe('sqlite-document')
    expect(resolveViewerFormat('storage.db')?.previewPipeline).toBe('server-assisted')
  })

  it('exposes media formats with native and server-assisted playback paths', () => {
    const mediaFormats = listViewerFormatsByFamily('media')

    expect(mediaFormats.map((definition) => definition.extension)).toEqual([
      'mp4',
      'mov',
      'webm',
      'avi',
      'mkv',
      'wmv',
      'flv',
    ])

    expect(resolveViewerFormat('clip.mp4')?.previewStrategyId).toBe('native-video')
    expect(resolveViewerFormat('clip.mov')?.previewPipeline).toBe('browser-native')
    expect(resolveViewerFormat('clip.mkv')?.previewStrategyId).toBe('legacy-video')
    expect(resolveViewerFormat('clip.avi')?.previewPipeline).toBe('server-assisted')
    expect(resolveViewerFormat('clip.wmv')?.previewPipeline).toBe('server-assisted')
    expect(resolveViewerFormat('track.flac')?.previewPipeline).toBe('server-assisted')
  })
})
