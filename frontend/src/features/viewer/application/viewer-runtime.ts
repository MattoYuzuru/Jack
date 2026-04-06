import {
  detectFileExtension,
  resolveViewerFormat,
  type PreviewStrategyId,
  type ViewerFormatDefinition,
} from '../domain/viewer-registry'
import type { ViewerAudioFact, ViewerAudioLayout, ViewerAudioPreviewPayload } from './viewer-audio'
import { buildNativeAudioPreview } from './viewer-audio-preview'
import type {
  ViewerDocumentFact,
  ViewerDocumentLayout,
  ViewerDocumentPreviewPayload,
} from './viewer-document'
import type { ViewerMetadataPayload } from './viewer-metadata'
import { loadStructuredMetadata } from './viewer-preview'
import {
  resolveServerViewerPreview,
  type ViewerServerResolvedPayload,
} from './viewer-server-preview'
import type { ViewerVideoFact, ViewerVideoLayout, ViewerVideoPreviewPayload } from './viewer-video'
import { buildNativeVideoPreview } from './viewer-video-preview'

export interface ViewerResolvedImage {
  kind: 'image'
  file: File
  extension: string
  format: ViewerFormatDefinition
  objectUrl: string
  dimensions: {
    width: number
    height: number
  }
  metadata: ViewerMetadataPayload
  previewLabel: string
}

export interface ViewerResolvedDocument {
  kind: 'document'
  file: File
  extension: string
  format: ViewerFormatDefinition
  summary: ViewerDocumentFact[]
  searchableText: string
  warnings: string[]
  layout: ViewerDocumentLayout
  previewLabel: string
}

export interface ViewerResolvedVideo {
  kind: 'video'
  file: File
  extension: string
  format: ViewerFormatDefinition
  summary: ViewerVideoFact[]
  warnings: string[]
  layout: ViewerVideoLayout
  previewLabel: string
}

export interface ViewerResolvedAudio {
  kind: 'audio'
  file: File
  extension: string
  format: ViewerFormatDefinition
  summary: ViewerAudioFact[]
  warnings: string[]
  searchableText: string
  artworkDataUrl: string | null
  metadataGroups: ViewerMetadataPayload['groups']
  layout: ViewerAudioLayout
  previewLabel: string
}

export interface ViewerResolvedUnknown {
  kind: 'unknown'
  file: File
  extension: string
  headline: string
  detail: string
  nextStep: string
}

export type ViewerResolvedEntry =
  | ViewerResolvedImage
  | ViewerResolvedDocument
  | ViewerResolvedVideo
  | ViewerResolvedAudio
  | ViewerResolvedUnknown

interface PreviewStrategyContext {
  file: File
  extension: string
  format: ViewerFormatDefinition
  reportProgress: (message: string) => void
}

interface PreviewStrategy<TResult extends ViewerResolvedEntry> {
  resolve(context: PreviewStrategyContext): Promise<TResult>
}

export interface ViewerRuntime {
  resolve(file: File, options?: ViewerResolveOptions): Promise<ViewerResolvedEntry>
}

export interface ViewerResolveOptions {
  onProgress?: (message: string) => void
}

export interface ViewerRuntimeDependencies {
  inspectNativeImage?: (objectUrl: string) => Promise<{ width: number; height: number }>
  loadNativeMetadata?: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerMetadataPayload>
  buildNativeVideo?: (context: PreviewStrategyContext) => Promise<ViewerVideoPreviewPayload>
  buildNativeAudio?: (context: PreviewStrategyContext) => Promise<ViewerAudioPreviewPayload>
  buildServerViewer?: (context: PreviewStrategyContext) => Promise<ViewerServerResolvedPayload>
}

const previewStrategies = (
  inspectNativeImage: (objectUrl: string) => Promise<{ width: number; height: number }>,
  loadNativeMetadata: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerMetadataPayload>,
  buildNativeVideo: (context: PreviewStrategyContext) => Promise<ViewerVideoPreviewPayload>,
  buildNativeAudio: (context: PreviewStrategyContext) => Promise<ViewerAudioPreviewPayload>,
  buildServerViewer: (context: PreviewStrategyContext) => Promise<ViewerServerResolvedPayload>,
): Record<PreviewStrategyId, PreviewStrategy<ViewerResolvedEntry>> => ({
  'native-image': {
    async resolve(context) {
      const buffer = await context.file.arrayBuffer()
      const metadata = await loadNativeMetadata(buffer, context)
      const objectUrl = URL.createObjectURL(context.file)

      try {
        const dimensions = await inspectNativeImage(objectUrl)

        return {
          kind: 'image',
          file: context.file,
          extension: context.extension,
          format: context.format,
          objectUrl,
          dimensions,
          metadata,
          previewLabel: context.format.statusLabel,
        }
      } catch (error) {
        URL.revokeObjectURL(objectUrl)
        throw error
      }
    },
  },
  'native-video': {
    async resolve(context) {
      return buildVideoSelection(await buildNativeVideo(context), context)
    },
  },
  'native-audio': {
    async resolve(context) {
      return buildAudioSelection(await buildNativeAudio(context), context)
    },
  },
  'server-viewer': {
    async resolve(context) {
      return buildServerSelection(await buildServerViewer(context), context)
    },
  },
  'planned-media': {
    async resolve(context) {
      return buildPlannedMediaSelection(context)
    },
  },
})

export function createViewerRuntime(dependencies: ViewerRuntimeDependencies = {}): ViewerRuntime {
  const inspectNativeImage = dependencies.inspectNativeImage ?? defaultInspectNativeImage
  const loadNativeMetadata = dependencies.loadNativeMetadata ?? defaultLoadNativeMetadata
  const strategies = previewStrategies(
    inspectNativeImage,
    loadNativeMetadata,
    dependencies.buildNativeVideo ?? defaultBuildNativeVideo,
    dependencies.buildNativeAudio ?? defaultBuildNativeAudio,
    dependencies.buildServerViewer ?? defaultBuildServerViewer,
  )

  return {
    async resolve(file, options = {}) {
      const extension = detectFileExtension(file.name)
      const format = await resolveViewerFormat(file.name, file.type)

      if (!format) {
        return {
          kind: 'unknown',
          file,
          extension,
          headline: 'Формат пока не заведён в viewer registry',
          detail:
            'Файл загружен, но для него ещё не описаны capability, маршрут preview и fallback-поведение.',
          nextStep:
            'Нужно добавить definition в registry и назначить ему browser-native либо server-assisted стратегию.',
        }
      }

      if (!format.available) {
        return {
          kind: 'unknown',
          file,
          extension,
          headline: `${format.label} временно недоступен в текущем backend capability matrix`,
          detail:
            format.availabilityDetail ||
            'Backend отключил этот preview route в текущем окружении, поэтому frontend не будет притворяться, что local registry всё ещё поддерживает формат.',
          nextStep:
            'Нужно восстановить соответствующую backend capability или обновить server-owned matrix для этого формата.',
        }
      }

      return strategies[format.previewStrategyId].resolve({
        file,
        extension,
        format,
        reportProgress: options.onProgress ?? (() => undefined),
      })
    },
  }
}

export function releaseViewerEntry(entry: ViewerResolvedEntry | null) {
  if (!entry) {
    return
  }

  if (entry.kind === 'image') {
    URL.revokeObjectURL(entry.objectUrl)
    return
  }

  if (entry.kind === 'video') {
    URL.revokeObjectURL(entry.layout.objectUrl)
    return
  }

  if (entry.kind === 'audio') {
    URL.revokeObjectURL(entry.layout.objectUrl)
    return
  }

  if (entry.kind === 'document' && entry.layout.mode === 'pdf') {
    URL.revokeObjectURL(entry.layout.objectUrl)
  }
}

function buildServerSelection(
  payload: ViewerServerResolvedPayload,
  context: PreviewStrategyContext,
): ViewerResolvedEntry {
  switch (payload.kind) {
    case 'image':
      return {
        kind: 'image',
        file: context.file,
        extension: context.extension,
        format: context.format,
        objectUrl: payload.objectUrl,
        dimensions: payload.dimensions,
        metadata: payload.metadata,
        previewLabel: payload.previewLabel,
      }
    case 'document':
      return buildDocumentSelection(
        {
          summary: payload.summary,
          searchableText: payload.searchableText,
          warnings: payload.warnings,
          layout: payload.layout,
          previewLabel: payload.previewLabel,
        },
        context,
      )
    case 'video':
      return buildVideoSelection(
        {
          summary: payload.summary,
          warnings: payload.warnings,
          layout: payload.layout,
          previewLabel: payload.previewLabel,
        },
        context,
      )
    case 'audio':
      return buildAudioSelection(
        {
          summary: payload.summary,
          warnings: payload.warnings,
          searchableText: payload.searchableText,
          artworkDataUrl: payload.artworkDataUrl,
          metadataGroups: payload.metadataGroups,
          layout: payload.layout,
          previewLabel: payload.previewLabel,
        },
        context,
      )
  }
}

function buildDocumentSelection(
  preview: ViewerDocumentPreviewPayload,
  context: PreviewStrategyContext,
): ViewerResolvedDocument {
  return {
    kind: 'document',
    file: context.file,
    extension: context.extension,
    format: context.format,
    summary: preview.summary,
    searchableText: preview.searchableText,
    warnings: preview.warnings,
    layout: preview.layout,
    previewLabel: preview.previewLabel,
  }
}

function buildVideoSelection(
  preview: ViewerVideoPreviewPayload,
  context: PreviewStrategyContext,
): ViewerResolvedVideo {
  return {
    kind: 'video',
    file: context.file,
    extension: context.extension,
    format: context.format,
    summary: preview.summary,
    warnings: preview.warnings,
    layout: preview.layout,
    previewLabel: preview.previewLabel,
  }
}

function buildAudioSelection(
  preview: ViewerAudioPreviewPayload,
  context: PreviewStrategyContext,
): ViewerResolvedAudio {
  return {
    kind: 'audio',
    file: context.file,
    extension: context.extension,
    format: context.format,
    summary: preview.summary,
    warnings: preview.warnings,
    searchableText: preview.searchableText,
    artworkDataUrl: preview.artworkDataUrl,
    metadataGroups: preview.metadataGroups,
    layout: preview.layout,
    previewLabel: preview.previewLabel,
  }
}

function buildPlannedMediaSelection(context: PreviewStrategyContext): ViewerResolvedUnknown {
  return {
    kind: 'unknown',
    file: context.file,
    extension: context.extension,
    headline: `${context.format.label} уже распознан в media registry, но playback adapter ещё не поднят`,
    detail:
      'Foundation для контейнера уже есть: accept/mime/capability map готовы, но для него пока нет стабильного browser-native или server-assisted playback path.',
    nextStep:
      'Нужно добавить format-specific media adapter поверх общего video contract, а не расширять UI точечными fallback-ветками.',
  }
}

async function defaultLoadNativeMetadata(
  _buffer: ArrayBuffer,
  context: PreviewStrategyContext,
): Promise<ViewerMetadataPayload> {
  return loadStructuredMetadata(context.file, context.reportProgress)
}

async function defaultBuildNativeVideo(
  context: PreviewStrategyContext,
): Promise<ViewerVideoPreviewPayload> {
  return buildNativeVideoPreview(context.file, context.format)
}

async function defaultBuildNativeAudio(
  context: PreviewStrategyContext,
): Promise<ViewerAudioPreviewPayload> {
  return buildNativeAudioPreview(context.file, context.format)
}

async function defaultBuildServerViewer(
  context: PreviewStrategyContext,
): Promise<ViewerServerResolvedPayload> {
  return resolveServerViewerPreview(context.file, context.reportProgress)
}

function defaultInspectNativeImage(objectUrl: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const image = new Image()

    image.onload = () => {
      resolve({
        width: image.naturalWidth,
        height: image.naturalHeight,
      })
    }

    image.onerror = () => {
      reject(new Error('Не удалось прочитать dimensions из image preview.'))
    }

    image.src = objectUrl
  })
}
