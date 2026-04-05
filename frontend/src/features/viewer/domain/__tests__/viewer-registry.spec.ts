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

  it('maps decode-adapter formats including raw aliases', () => {
    const formats = listViewerFormatsByFamily('image').filter(
      (definition) => definition.previewPipeline === 'client-decode',
    )

    expect(formats.map((definition) => definition.extension)).toEqual(['heic', 'tiff', 'raw'])

    expect(resolveViewerFormat('shoot.NEF')?.extension).toBe('raw')
    expect(resolveViewerFormat('scan.tif')?.extension).toBe('tiff')
  })

  it('exposes document formats with active and planned capability states', () => {
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
    expect(resolveViewerFormat('proposal.docx')?.previewPipeline).toBe('planned')
  })
})
