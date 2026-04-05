import {
  detectFileExtension,
  resolveViewerFormat,
  type PreviewStrategyId,
  type ViewerFormatDefinition,
} from '../domain/viewer-registry'
import type {
  ViewerDocumentFact,
  ViewerDocumentLayout,
  ViewerDocumentPreviewPayload,
} from './viewer-document'
import {
  buildCsvDocumentPreview,
  buildDocDocumentPreview,
  buildEpubDocumentPreview,
  buildDocxDocumentPreview,
  buildHtmlDocumentPreview,
  buildOdtDocumentPreview,
  buildPdfDocumentPreview,
  buildPptxDocumentPreview,
  buildRtfDocumentPreview,
  buildSqliteDocumentPreview,
  buildTextDocumentPreview,
  buildXlsDocumentPreview,
  buildXlsxDocumentPreview,
} from './viewer-document-preview'
import type { ViewerMetadataPayload } from './viewer-metadata'
import {
  loadStructuredMetadata,
  type ViewerBinaryPreview,
} from './viewer-preview'
import {
  decodeHeicPreview,
  decodeRawPreview,
  decodeTiffPreview,
} from './viewer-image-preview'
import type { ViewerAudioFact, ViewerAudioLayout, ViewerAudioPreviewPayload } from './viewer-audio'
import { buildNativeAudioPreview } from './viewer-audio-preview'
import type { ViewerVideoFact, ViewerVideoLayout, ViewerVideoPreviewPayload } from './viewer-video'
import { buildNativeVideoPreview } from './viewer-video-preview'
import { buildLegacyAudioPreview, buildLegacyVideoPreview } from './viewer-media-preview'

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
  decodeHeicImage?: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerBinaryPreview>
  decodeTiffImage?: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerBinaryPreview>
  decodeRawImage?: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerBinaryPreview>
  buildNativeVideo?: (context: PreviewStrategyContext) => Promise<ViewerVideoPreviewPayload>
  buildLegacyVideo?: (context: PreviewStrategyContext) => Promise<ViewerVideoPreviewPayload>
  buildNativeAudio?: (context: PreviewStrategyContext) => Promise<ViewerAudioPreviewPayload>
  buildLegacyAudio?: (context: PreviewStrategyContext) => Promise<ViewerAudioPreviewPayload>
  buildPdfDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildTextDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildCsvDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildHtmlDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildRtfDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildDocDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildDocxDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildOdtDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildXlsDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildXlsxDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildPptxDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildEpubDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
  buildSqliteDocument?: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>
}

const previewStrategies = (
  inspectNativeImage: (objectUrl: string) => Promise<{ width: number; height: number }>,
  loadNativeMetadata: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerMetadataPayload>,
  decodeHeicImage: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerBinaryPreview>,
  decodeTiffImage: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerBinaryPreview>,
  decodeRawImage: (
    buffer: ArrayBuffer,
    context: PreviewStrategyContext,
  ) => Promise<ViewerBinaryPreview>,
  buildNativeVideo: (context: PreviewStrategyContext) => Promise<ViewerVideoPreviewPayload>,
  buildLegacyVideo: (context: PreviewStrategyContext) => Promise<ViewerVideoPreviewPayload>,
  buildNativeAudio: (context: PreviewStrategyContext) => Promise<ViewerAudioPreviewPayload>,
  buildLegacyAudio: (context: PreviewStrategyContext) => Promise<ViewerAudioPreviewPayload>,
  buildPdfDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildTextDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildCsvDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildHtmlDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildRtfDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildDocDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildDocxDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildOdtDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildXlsDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildXlsxDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildPptxDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildEpubDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildSqliteDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
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
  'heic-image': {
    async resolve(context) {
      const buffer = await context.file.arrayBuffer()
      const preview = await decodeHeicImage(buffer, context)
      return buildDecodedImageSelection(preview, context, inspectNativeImage)
    },
  },
  'tiff-image': {
    async resolve(context) {
      const buffer = await context.file.arrayBuffer()
      const preview = await decodeTiffImage(buffer, context)
      return buildDecodedImageSelection(preview, context, inspectNativeImage)
    },
  },
  'raw-image': {
    async resolve(context) {
      const buffer = await context.file.arrayBuffer()
      const preview = await decodeRawImage(buffer, context)
      return buildDecodedImageSelection(preview, context, inspectNativeImage)
    },
  },
  'native-video': {
    async resolve(context) {
      return buildVideoSelection(await buildNativeVideo(context), context)
    },
  },
  'legacy-video': {
    async resolve(context) {
      return buildVideoSelection(await buildLegacyVideo(context), context)
    },
  },
  'native-audio': {
    async resolve(context) {
      return buildAudioSelection(await buildNativeAudio(context), context)
    },
  },
  'legacy-audio': {
    async resolve(context) {
      return buildAudioSelection(await buildLegacyAudio(context), context)
    },
  },
  'pdf-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildPdfDocument(context), context)
    },
  },
  'text-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildTextDocument(context), context)
    },
  },
  'csv-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildCsvDocument(context), context)
    },
  },
  'html-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildHtmlDocument(context), context)
    },
  },
  'rtf-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildRtfDocument(context), context)
    },
  },
  'doc-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildDocDocument(context), context)
    },
  },
  'docx-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildDocxDocument(context), context)
    },
  },
  'odt-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildOdtDocument(context), context)
    },
  },
  'xls-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildXlsDocument(context), context)
    },
  },
  'xlsx-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildXlsxDocument(context), context)
    },
  },
  'pptx-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildPptxDocument(context), context)
    },
  },
  'epub-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildEpubDocument(context), context)
    },
  },
  'sqlite-document': {
    async resolve(context) {
      return buildDocumentSelection(await buildSqliteDocument(context), context)
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
    dependencies.decodeHeicImage ?? defaultDecodeHeicImage,
    dependencies.decodeTiffImage ?? defaultDecodeTiffImage,
    dependencies.decodeRawImage ?? defaultDecodeRawImage,
    dependencies.buildNativeVideo ?? defaultBuildNativeVideo,
    dependencies.buildLegacyVideo ?? defaultBuildLegacyVideo,
    dependencies.buildNativeAudio ?? defaultBuildNativeAudio,
    dependencies.buildLegacyAudio ?? defaultBuildLegacyAudio,
    dependencies.buildPdfDocument ?? defaultBuildPdfDocument,
    dependencies.buildTextDocument ?? defaultBuildTextDocument,
    dependencies.buildCsvDocument ?? defaultBuildCsvDocument,
    dependencies.buildHtmlDocument ?? defaultBuildHtmlDocument,
    dependencies.buildRtfDocument ?? defaultBuildRtfDocument,
    dependencies.buildDocDocument ?? defaultBuildDocDocument,
    dependencies.buildDocxDocument ?? defaultBuildDocxDocument,
    dependencies.buildOdtDocument ?? defaultBuildOdtDocument,
    dependencies.buildXlsDocument ?? defaultBuildXlsDocument,
    dependencies.buildXlsxDocument ?? defaultBuildXlsxDocument,
    dependencies.buildPptxDocument ?? defaultBuildPptxDocument,
    dependencies.buildEpubDocument ?? defaultBuildEpubDocument,
    dependencies.buildSqliteDocument ?? defaultBuildSqliteDocument,
  )

  return {
    async resolve(file, options = {}) {
      const extension = detectFileExtension(file.name)
      const format = resolveViewerFormat(file.name, file.type)

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

async function buildDecodedImageSelection(
  preview: ViewerBinaryPreview,
  context: PreviewStrategyContext,
  inspectNativeImage: (objectUrl: string) => Promise<{ width: number; height: number }>,
): Promise<ViewerResolvedImage> {
  // UI работает только с единым object URL контрактом, поэтому даже декодированные
  // форматы приводим к тому же виду, что и browser-native изображения.
  const previewBuffer = preview.bytes.slice().buffer
  const objectUrl = URL.createObjectURL(new Blob([previewBuffer], { type: preview.mimeType }))

  try {
    const dimensions = await inspectNativeImage(objectUrl)

    return {
      kind: 'image',
      file: context.file,
      extension: context.extension,
      format: context.format,
      objectUrl,
      dimensions,
      metadata: preview.metadata,
      previewLabel: preview.previewLabel,
    }
  } catch (error) {
    URL.revokeObjectURL(objectUrl)
    throw error
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

async function defaultDecodeHeicImage(
  buffer: ArrayBuffer,
  context: PreviewStrategyContext,
): Promise<ViewerBinaryPreview> {
  return decodeHeicPreview(buffer, context)
}

async function defaultDecodeTiffImage(
  buffer: ArrayBuffer,
  context: PreviewStrategyContext,
): Promise<ViewerBinaryPreview> {
  return decodeTiffPreview(buffer, context)
}

async function defaultDecodeRawImage(
  buffer: ArrayBuffer,
  context: PreviewStrategyContext,
): Promise<ViewerBinaryPreview> {
  return decodeRawPreview(buffer, context)
}

async function defaultBuildNativeVideo(
  context: PreviewStrategyContext,
): Promise<ViewerVideoPreviewPayload> {
  return buildNativeVideoPreview(context.file, context.format)
}

async function defaultBuildLegacyVideo(
  context: PreviewStrategyContext,
): Promise<ViewerVideoPreviewPayload> {
  return buildLegacyVideoPreview(context.file, context.format, context.reportProgress)
}

async function defaultBuildNativeAudio(
  context: PreviewStrategyContext,
): Promise<ViewerAudioPreviewPayload> {
  return buildNativeAudioPreview(context.file, context.format)
}

async function defaultBuildLegacyAudio(
  context: PreviewStrategyContext,
): Promise<ViewerAudioPreviewPayload> {
  return buildLegacyAudioPreview(context.file, context.format, context.reportProgress)
}

async function defaultBuildPdfDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildPdfDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildTextDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildTextDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildCsvDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildCsvDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildHtmlDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildHtmlDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildRtfDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildRtfDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildDocDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildDocDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildDocxDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildDocxDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildOdtDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildOdtDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildXlsDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildXlsDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildXlsxDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildXlsxDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildPptxDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildPptxDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildEpubDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildEpubDocumentPreview(context.file, context.reportProgress)
}

async function defaultBuildSqliteDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildSqliteDocumentPreview(context.file, context.reportProgress)
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
