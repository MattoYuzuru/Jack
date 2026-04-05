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
  buildHtmlDocumentPreview,
  buildPdfDocumentPreview,
  buildRtfDocumentPreview,
  buildTextDocumentPreview,
} from './viewer-document-preview'
import type { ViewerMetadataPayload } from './viewer-metadata'
import {
  decodeHeicPreview,
  decodeRawPreview,
  decodeTiffPreview,
  loadStructuredMetadata,
  type ViewerBinaryPreview,
} from './viewer-preview'

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
  | ViewerResolvedUnknown

interface PreviewStrategyContext {
  file: File
  extension: string
  format: ViewerFormatDefinition
}

interface PreviewStrategy<TResult extends ViewerResolvedEntry> {
  resolve(context: PreviewStrategyContext): Promise<TResult>
}

export interface ViewerRuntime {
  resolve(file: File): Promise<ViewerResolvedEntry>
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
  buildPdfDocument?: (
    context: PreviewStrategyContext,
  ) => Promise<ViewerDocumentPreviewPayload>
  buildTextDocument?: (
    context: PreviewStrategyContext,
  ) => Promise<ViewerDocumentPreviewPayload>
  buildCsvDocument?: (
    context: PreviewStrategyContext,
  ) => Promise<ViewerDocumentPreviewPayload>
  buildHtmlDocument?: (
    context: PreviewStrategyContext,
  ) => Promise<ViewerDocumentPreviewPayload>
  buildRtfDocument?: (
    context: PreviewStrategyContext,
  ) => Promise<ViewerDocumentPreviewPayload>
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
  buildPdfDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildTextDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildCsvDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildHtmlDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
  buildRtfDocument: (context: PreviewStrategyContext) => Promise<ViewerDocumentPreviewPayload>,
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
  'planned-document': {
    async resolve(context) {
      return buildPlannedDocumentSelection(context)
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
    dependencies.buildPdfDocument ?? defaultBuildPdfDocument,
    dependencies.buildTextDocument ?? defaultBuildTextDocument,
    dependencies.buildCsvDocument ?? defaultBuildCsvDocument,
    dependencies.buildHtmlDocument ?? defaultBuildHtmlDocument,
    dependencies.buildRtfDocument ?? defaultBuildRtfDocument,
  )

  return {
    async resolve(file) {
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
            'Нужно добавить definition в registry и назначить ему browser-native либо client-decode стратегию.',
        }
      }

      return strategies[format.previewStrategyId].resolve({
        file,
        extension,
        format,
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

function buildPlannedDocumentSelection(context: PreviewStrategyContext): ViewerResolvedUnknown {
  return {
    kind: 'unknown',
    file: context.file,
    extension: context.extension,
    headline: `${context.format.label} уже распознан в document registry, но preview ещё не поднят`,
    detail:
      'Foundation для формата уже есть: accept/mime/capability map готовы, но parser и render path для него пока не реализованы.',
    nextStep:
      'Нужно добавить format-specific document adapter поверх общего document contract, а не расширять UI вручную.',
  }
}

async function defaultLoadNativeMetadata(buffer: ArrayBuffer): Promise<ViewerMetadataPayload> {
  return loadStructuredMetadata(buffer)
}

async function defaultDecodeHeicImage(buffer: ArrayBuffer): Promise<ViewerBinaryPreview> {
  return decodeHeicPreview(buffer)
}

async function defaultDecodeTiffImage(buffer: ArrayBuffer): Promise<ViewerBinaryPreview> {
  return decodeTiffPreview(buffer)
}

async function defaultDecodeRawImage(buffer: ArrayBuffer): Promise<ViewerBinaryPreview> {
  return decodeRawPreview(buffer)
}

async function defaultBuildPdfDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildPdfDocumentPreview(context.file)
}

async function defaultBuildTextDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildTextDocumentPreview(context.file)
}

async function defaultBuildCsvDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildCsvDocumentPreview(context.file)
}

async function defaultBuildHtmlDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildHtmlDocumentPreview(context.file)
}

async function defaultBuildRtfDocument(
  context: PreviewStrategyContext,
): Promise<ViewerDocumentPreviewPayload> {
  return buildRtfDocumentPreview(context.file)
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
