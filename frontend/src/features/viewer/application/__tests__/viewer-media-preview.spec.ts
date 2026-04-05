import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ViewerFormatDefinition } from '../../domain/viewer-registry'

const previewBuilders = vi.hoisted(() => ({
  buildVideoPreviewFromBlob: vi.fn(),
  buildAudioPreviewFromBlob: vi.fn(),
}))

vi.mock('../viewer-video-preview', () => ({
  buildVideoPreviewFromBlob: previewBuilders.buildVideoPreviewFromBlob,
}))

vi.mock('../viewer-audio-preview', () => ({
  buildAudioPreviewFromBlob: previewBuilders.buildAudioPreviewFromBlob,
}))

import { buildLegacyAudioPreview, buildLegacyVideoPreview } from '../viewer-media-preview'

const videoFormat: ViewerFormatDefinition = {
  extension: 'avi',
  aliases: [],
  label: 'AVI',
  family: 'media',
  mimeTypes: ['video/x-msvideo'],
  previewPipeline: 'server-assisted',
  previewStrategyId: 'legacy-video',
  statusLabel: 'Server media preview',
  notes: 'Video goes through backend MEDIA_PREVIEW.',
  accents: ['Video', 'Legacy'],
}

const audioFormat: ViewerFormatDefinition = {
  extension: 'flac',
  aliases: [],
  label: 'FLAC',
  family: 'audio',
  mimeTypes: ['audio/flac'],
  previewPipeline: 'server-assisted',
  previewStrategyId: 'legacy-audio',
  statusLabel: 'Server audio preview',
  notes: 'Audio goes through backend MEDIA_PREVIEW.',
  accents: ['Audio', 'Lossless'],
}

const originalFetch = globalThis.fetch

beforeEach(() => {
  globalThis.fetch = vi.fn() as typeof fetch
})

afterEach(() => {
  globalThis.fetch = originalFetch
  vi.clearAllMocks()
})

describe('viewer media preview client', () => {
  it('builds a server-assisted video preview from backend artifacts', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    const progressMessages: string[] = []
    const sourceFile = new File(['video'], 'legacy.avi', { type: 'video/x-msvideo' })

    previewBuilders.buildVideoPreviewFromBlob.mockResolvedValue({
      summary: [{ label: 'Playback path', value: 'Backend MEDIA_PREVIEW' }],
      warnings: [],
      layout: {
        mode: 'native',
        objectUrl: 'blob:server-video-preview',
        durationSeconds: 12.5,
        width: 640,
        height: 360,
        metadata: {
          mimeType: 'video/mp4',
          aspectRatio: '16:9',
          orientation: 'Landscape',
          estimatedBitrateBitsPerSecond: 2_100_000,
          sizeBytes: 1_048_576,
        },
      },
      previewLabel: 'Server media preview',
    })

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'media-foundation',
          jobTypes: [
            {
              jobType: 'MEDIA_PREVIEW',
              implemented: true,
              notes: 'Backend already supports MEDIA_PREVIEW.',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-1' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-1' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-1',
          status: 'RUNNING',
          progressPercent: 30,
          message: 'Проверяю media container через ffprobe.',
          errorMessage: null,
          artifacts: [],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-1',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Media preview готов через backend MP4 transcode path.',
          errorMessage: null,
          artifacts: [
            {
              id: 'artifact-manifest',
              kind: 'media-preview-manifest',
              fileName: 'media-preview-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 420,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-1/artifacts/artifact-manifest',
            },
            {
              id: 'artifact-binary',
              kind: 'media-preview-binary',
              fileName: 'legacy.preview.mp4',
              mediaType: 'video/mp4',
              sizeBytes: 1024,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-1/artifacts/artifact-binary',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          uploadId: 'upload-1',
          originalFileName: 'legacy.avi',
          family: 'media',
          probe: {
            durationSeconds: 12.5,
            codecName: 'mpeg4',
            width: 640,
            height: 360,
            sampleRate: null,
            channelCount: null,
          },
          runtimeLabel: 'MP4 transcode',
          previewMediaType: 'video/mp4',
          generatedAt: '2026-04-05T15:00:00Z',
          warnings: ['Source audio track required fallback handling.'],
        }),
      )
      .mockResolvedValueOnce(
        new Response('video-binary', {
          status: 200,
          headers: { 'Content-Type': 'video/mp4' },
        }),
      )

    const result = await buildLegacyVideoPreview(sourceFile, videoFormat, (message) => {
      progressMessages.push(message)
    })

    expect(result.previewLabel).toBe('Server media preview')
    expect(fetchMock).toHaveBeenCalledTimes(7)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/capabilities/viewer')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/uploads')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('http://localhost:8080/api/jobs')

    const uploadBody = fetchMock.mock.calls[1]?.[1]?.body
    expect(uploadBody).toBeInstanceOf(FormData)
    const uploadedFile = (uploadBody as FormData).get('file')
    expect(uploadedFile).toBeInstanceOf(File)
    expect((uploadedFile as File).name).toBe(sourceFile.name)
    expect((uploadedFile as File).type).toBe(sourceFile.type)

    expect(previewBuilders.buildVideoPreviewFromBlob).toHaveBeenCalledTimes(1)
    const [previewBlob, format, options] = previewBuilders.buildVideoPreviewFromBlob.mock
      .calls[0] as [Blob, ViewerFormatDefinition, Record<string, unknown>]

    expect(previewBlob.type).toBe('video/mp4')
    expect(await previewBlob.text()).toBe('video-binary')
    expect(format.extension).toBe('avi')
    expect(options.previewLabel).toBe('Server media preview')
    expect(options.playbackPathLabel).toBe('Backend MEDIA_PREVIEW · MP4 transcode')
    expect(options.warnings).toEqual([
      'Legacy video preview собирается через backend MEDIA_PREVIEW job: браузер больше не тянет локальный ffmpeg runtime для этого контейнера.',
      'Source audio track required fallback handling.',
    ])
    expect(progressMessages).toContain('Проверяю media container через ffprobe.')
    expect(progressMessages).toContain('Загружаю media preview artifact и manifest с backend...')
  })

  it('builds a server-assisted audio preview from backend artifacts', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)
    const sourceFile = new File(['audio'], 'archive.flac', { type: 'audio/flac' })

    previewBuilders.buildAudioPreviewFromBlob.mockResolvedValue({
      summary: [{ label: 'Playback path', value: 'Backend MEDIA_PREVIEW' }],
      warnings: [],
      searchableText: 'Archive track',
      artworkDataUrl: null,
      metadataGroups: [],
      layout: {
        mode: 'native',
        objectUrl: 'blob:server-audio-preview',
        durationSeconds: 8,
        waveform: [0.1, 0.8],
        metadata: {
          mimeType: 'audio/mpeg',
          estimatedBitrateBitsPerSecond: 192_000,
          sampleRate: 48_000,
          channelCount: 2,
          codec: 'MP3',
          container: 'MPEG',
          sizeBytes: 512_000,
        },
      },
      previewLabel: 'Server audio preview',
    })

    fetchMock
      .mockResolvedValueOnce(
        createJsonResponse({
          scope: 'viewer',
          phase: 'media-foundation',
          jobTypes: [
            {
              jobType: 'MEDIA_PREVIEW',
              implemented: true,
              notes: 'Backend already supports MEDIA_PREVIEW.',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(createJsonResponse({ id: 'upload-audio' }, 201))
      .mockResolvedValueOnce(createJsonResponse({ id: 'job-audio' }, 202))
      .mockResolvedValueOnce(
        createJsonResponse({
          id: 'job-audio',
          status: 'COMPLETED',
          progressPercent: 100,
          message: 'Media preview готов через backend MP3 transcode path.',
          errorMessage: null,
          artifacts: [
            {
              id: 'audio-manifest',
              kind: 'media-preview-manifest',
              fileName: 'media-preview-manifest.json',
              mediaType: 'application/json',
              sizeBytes: 320,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-audio/artifacts/audio-manifest',
            },
            {
              id: 'audio-binary',
              kind: 'media-preview-binary',
              fileName: 'archive.preview.mp3',
              mediaType: 'audio/mpeg',
              sizeBytes: 512,
              createdAt: '2026-04-05T15:00:00Z',
              downloadPath: '/api/jobs/job-audio/artifacts/audio-binary',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(
        createJsonResponse({
          uploadId: 'upload-audio',
          originalFileName: 'archive.flac',
          family: 'audio',
          probe: {
            durationSeconds: 8,
            codecName: 'flac',
            width: null,
            height: null,
            sampleRate: 48000,
            channelCount: 2,
          },
          runtimeLabel: 'MP3 transcode',
          previewMediaType: 'audio/mpeg',
          generatedAt: '2026-04-05T15:00:00Z',
          warnings: ['Waveform preview собран из backend-generated artifact.'],
        }),
      )
      .mockResolvedValueOnce(
        new Response('audio-binary', {
          status: 200,
          headers: { 'Content-Type': 'audio/mpeg' },
        }),
      )

    const result = await buildLegacyAudioPreview(sourceFile, audioFormat)

    expect(result.previewLabel).toBe('Server audio preview')
    expect(previewBuilders.buildAudioPreviewFromBlob).toHaveBeenCalledTimes(1)
    const [previewBlob, format, options] = previewBuilders.buildAudioPreviewFromBlob.mock
      .calls[0] as [Blob, ViewerFormatDefinition, Record<string, unknown>]

    expect(fetchMock).toHaveBeenCalledTimes(6)
    expect(previewBlob.type).toBe('audio/mpeg')
    expect(await previewBlob.text()).toBe('audio-binary')
    expect(format.extension).toBe('flac')
    expect(options.previewLabel).toBe('Server audio preview')
    expect(options.playbackPathLabel).toBe('Backend MEDIA_PREVIEW · MP3 transcode')
    expect(options.metadataSource).toBe(sourceFile)
    expect(options.warnings).toEqual([
      'Legacy audio preview собирается через backend MEDIA_PREVIEW job: браузер получает уже готовый playback artifact вместо локального transcode bridge.',
      'Waveform preview собран из backend-generated artifact.',
    ])
  })

  it('fails fast when backend MEDIA_PREVIEW capability is disabled', async () => {
    const fetchMock = vi.mocked(globalThis.fetch)

    fetchMock.mockResolvedValueOnce(
      createJsonResponse({
        scope: 'viewer',
        phase: 'media-foundation',
        jobTypes: [
          {
            jobType: 'MEDIA_PREVIEW',
            implemented: false,
            notes:
              'Media preview service требует доступных ffmpeg/ffprobe binaries в backend окружении.',
          },
        ],
      }),
    )

    await expect(
      buildLegacyVideoPreview(
        new File(['video'], 'broken.avi', { type: 'video/x-msvideo' }),
        videoFormat,
      ),
    ).rejects.toThrow(
      'Media preview service требует доступных ffmpeg/ffprobe binaries в backend окружении.',
    )

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(previewBuilders.buildVideoPreviewFromBlob).not.toHaveBeenCalled()
  })
})

function createJsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'Content-Type': 'application/json',
    },
  })
}
