import {
  detectFileExtension,
  resolveViewerFormat,
  type PreviewStrategyId,
  type ViewerFormatDefinition,
} from '../domain/viewer-registry'
import {
  decodeHeicPreview,
  decodeRawPreview,
  decodeTiffPreview,
  loadStructuredMetadata,
  type ViewerBinaryPreview,
} from './viewer-preview'
import type { ViewerMetadataPayload } from './viewer-metadata'

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

export interface ViewerResolvedUnknown {
  kind: 'unknown'
  file: File
  extension: string
  headline: string
  detail: string
  nextStep: string
}

export type ViewerResolvedEntry = ViewerResolvedImage | ViewerResolvedUnknown

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
