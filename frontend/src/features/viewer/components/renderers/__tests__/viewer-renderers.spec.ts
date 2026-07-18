import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ViewerAudioRenderer from '../ViewerAudioRenderer.vue'
import ViewerDataRenderer from '../ViewerDataRenderer.vue'
import ViewerDocumentRenderer from '../ViewerDocumentRenderer.vue'
import ViewerImageRenderer from '../ViewerImageRenderer.vue'
import ViewerVideoRenderer from '../ViewerVideoRenderer.vue'
import type {
  ViewerResolvedAudio,
  ViewerResolvedDocument,
  ViewerResolvedImage,
  ViewerResolvedVideo,
} from '../../../application/viewer-runtime'
import type { ViewerFormatDefinition } from '../../../domain/viewer-registry'

const format: ViewerFormatDefinition = {
  extension: 'txt',
  aliases: [],
  label: 'Test',
  family: 'document',
  mimeTypes: ['text/plain'],
  previewPipeline: 'browser-native',
  previewStrategyId: 'server-viewer',
  statusLabel: 'Готово',
  notes: '',
  accents: [],
  available: true,
  availabilityDetail: null,
  requiredJobTypes: [],
}

const file = new File(['jack'], 'sample.txt', { type: 'text/plain' })

describe('Viewer renderers', () => {
  it('рендерит image и сообщает DOM element владельцу lifecycle', () => {
    const selection: ViewerResolvedImage = {
      kind: 'image',
      file,
      extension: 'png',
      format: { ...format, family: 'image' },
      objectUrl: 'blob:image',
      dimensions: { width: 1, height: 1 },
      metadata: { groups: [], summary: [], editable: {}, thumbnailDataUrl: null } as never,
      previewLabel: 'Готово',
    }
    const wrapper = mount(ViewerImageRenderer, {
      props: { selection, viewportTransform: 'scale(1)' },
    })

    expect(wrapper.get('img').attributes('alt')).toBe('sample.txt')
    expect(wrapper.emitted('elementChange')?.[0]?.[0]).toBeInstanceOf(HTMLImageElement)
  })

  it('оставляет HTML document в sandbox без same-origin', () => {
    const selection: ViewerResolvedDocument = {
      kind: 'document',
      file,
      extension: 'html',
      format,
      summary: [],
      searchableText: 'Jack',
      warnings: [],
      layout: {
        mode: 'html',
        text: 'Jack',
        srcDoc: '<p>Jack</p>',
        outline: [],
        editableDraft: null,
      },
      previewLabel: 'Готово',
    }
    const wrapper = mount(ViewerDocumentRenderer, {
      props: {
        selection,
        modeLabel: 'HTML',
        metrics: [],
        actionMessage: '',
        canQuickEdit: false,
        searchQuery: '',
        slideIndex: 0,
      },
    })

    expect(wrapper.get('iframe').attributes('sandbox')).toBe('')
  })

  it('показывает пустой workbook как честное empty state', () => {
    const selection: ViewerResolvedDocument = {
      kind: 'document',
      file,
      extension: 'xlsx',
      format,
      summary: [],
      searchableText: '',
      warnings: ['partial'],
      layout: { mode: 'workbook', text: '', sheets: [], activeSheetIndex: 0, editableDraft: null },
      previewLabel: 'Частичный preview',
    }
    const wrapper = mount(ViewerDataRenderer, {
      props: { selection, sheetIndex: 0, databaseTableIndex: 0 },
    })

    expect(wrapper.text()).toContain('нет доступных листов')
  })

  it('ограничивает table DOM текущим virtual window', () => {
    const selection: ViewerResolvedDocument = {
      kind: 'document',
      file,
      extension: 'csv',
      format,
      summary: [],
      searchableText: '',
      warnings: [],
      layout: {
        mode: 'table',
        text: '',
        table: {
          columns: ['Value'],
          rows: Array.from({ length: 250 }, (_, index) => [`row-${index + 1}`]),
          totalRows: 250,
          totalColumns: 1,
          delimiter: ',',
          rowOffset: 0,
        },
        editableDraft: null,
      },
      previewLabel: 'Bounded preview',
    }
    const wrapper = mount(ViewerDataRenderer, {
      props: { selection, sheetIndex: 0, databaseTableIndex: 0 },
    })

    expect(wrapper.findAll('tbody tr')).toHaveLength(200)
    expect(wrapper.get('tbody th').text()).toBe('51')
  })

  it('монтирует bounded video controls и отдаёт media element', () => {
    const selection: ViewerResolvedVideo = {
      kind: 'video',
      file,
      extension: 'mp4',
      format: { ...format, family: 'media' },
      summary: [],
      warnings: [],
      previewLabel: 'Готово',
      layout: {
        mode: 'native',
        objectUrl: 'blob:video',
        durationSeconds: 1,
        width: 1,
        height: 1,
        metadata: {
          mimeType: 'video/mp4',
          aspectRatio: '1:1',
          orientation: 'square',
          estimatedBitrateBitsPerSecond: null,
          sizeBytes: 4,
        },
      },
    }
    const wrapper = mount(ViewerVideoRenderer, {
      props: {
        selection,
        metrics: [],
        isPlaying: false,
        isMuted: false,
        volume: 1,
        playbackRate: 1,
        playbackRates: [1],
        currentTime: 0,
        durationSeconds: 1,
        progressPercent: 0,
        currentTimeLabel: '00:00',
        durationLabel: '00:01',
        canUsePictureInPicture: false,
        isPictureInPictureActive: false,
        isLooping: false,
        assumedFrameRate: 24,
        frameRateOptions: [24],
        frameStepLabel: '42 ms',
        approximateFrameNumber: 1,
        subtitleTracks: [],
        activeSubtitleTrack: null,
        activeSubtitleTrackId: 'off',
        playbackMessage: '',
        subtitleMessage: '',
        posterMessage: '',
        posterCount: 0,
      },
    })
    expect(wrapper.get('video').attributes('preload')).toBe('metadata')
    expect(wrapper.emitted('elementChange')?.[0]?.[0]).toBeInstanceOf(HTMLVideoElement)
  })

  it('монтирует audio без удержания полного media payload в DOM', () => {
    const selection: ViewerResolvedAudio = {
      kind: 'audio',
      file,
      extension: 'mp3',
      format: { ...format, family: 'audio' },
      summary: [],
      warnings: [],
      searchableText: '',
      artworkDataUrl: null,
      metadataGroups: [],
      previewLabel: 'Готово',
      layout: {
        mode: 'native',
        objectUrl: 'blob:audio',
        durationSeconds: 1,
        waveform: [0.2, 0.8],
        metadata: {
          mimeType: 'audio/mpeg',
          estimatedBitrateBitsPerSecond: null,
          sampleRate: null,
          channelCount: null,
          codec: null,
          container: null,
          sizeBytes: 4,
        },
      },
    }
    const wrapper = mount(ViewerAudioRenderer, {
      props: {
        selection,
        metrics: [],
        isPlaying: false,
        isMuted: false,
        volume: 1,
        playbackRate: 1,
        playbackRates: [1],
        currentTime: 0,
        durationSeconds: 1,
        progressPercent: 0,
        currentTimeLabel: '00:00',
        durationLabel: '00:01',
        isLooping: false,
        playbackMessage: '',
      },
    })
    expect(wrapper.findAll('.audio-waveform__bar')).toHaveLength(2)
    expect(wrapper.emitted('elementChange')?.[0]?.[0]).toBeInstanceOf(HTMLAudioElement)
  })
})
