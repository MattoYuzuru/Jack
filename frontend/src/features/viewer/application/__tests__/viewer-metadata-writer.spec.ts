import { describe, expect, it, vi } from 'vitest'
import {
  canEmbedMetadata,
  exportViewerMetadata,
  formatInputDateForExif,
  withJsonSuffix,
} from '../viewer-metadata-writer'

describe('viewer metadata writer', () => {
  it('detects when metadata can be embedded directly into jpeg files', () => {
    expect(canEmbedMetadata('cover.jpg')).toBe(true)
    expect(canEmbedMetadata('cover.jpeg')).toBe(true)
    expect(canEmbedMetadata('cover.png')).toBe(false)
  })

  it('formats datetime-local values into EXIF datetime format', () => {
    expect(formatInputDateForExif('2026-04-04T18:45')).toBe('2026:04:04 18:45:00')
    expect(formatInputDateForExif('invalid')).toBe('')
  })

  it('adds the expected sidecar suffix', () => {
    expect(withJsonSuffix('frame.tiff')).toBe('frame.tiff.jack-metadata.json')
  })

  it('exports non-jpeg metadata edits as a sidecar json payload', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-04T12:30:00.000Z'))

    try {
      const result = await exportViewerMetadata(
        new File(['png'], 'frame.png', { type: 'image/png' }),
        {
          description: 'Product render',
          artist: 'Jack',
          copyright: 'Jack Studio',
          capturedAt: '2026-04-04T15:30',
        },
      )

      expect(result.mode).toBe('json-sidecar')
      expect(result.fileName).toBe('frame.png.jack-metadata.json')

      const payload = JSON.parse(await result.blob.text())

      expect(payload).toEqual({
        fileName: 'frame.png',
        exportedAt: '2026-04-04T12:30:00.000Z',
        metadata: {
          description: 'Product render',
          artist: 'Jack',
          copyright: 'Jack Studio',
          capturedAt: '2026-04-04T15:30',
        },
      })
    } finally {
      vi.useRealTimers()
    }
  })
})
