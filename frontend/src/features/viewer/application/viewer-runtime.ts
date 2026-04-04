import {
  detectFileExtension,
  resolveViewerFormat,
  type PreviewStrategyId,
  type ViewerFormatDefinition,
} from '../domain/viewer-registry'

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
}

export interface ViewerResolvedDeferred {
  kind: 'deferred'
  file: File
  extension: string
  format: ViewerFormatDefinition
  headline: string
  detail: string
  nextStep: string
}

export interface ViewerResolvedUnknown {
  kind: 'unknown'
  file: File
  extension: string
  headline: string
  detail: string
  nextStep: string
}

export type ViewerResolvedEntry = ViewerResolvedImage | ViewerResolvedDeferred | ViewerResolvedUnknown

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
}

const previewStrategies = (
  inspectNativeImage: (objectUrl: string) => Promise<{ width: number; height: number }>,
): Record<PreviewStrategyId, PreviewStrategy<ViewerResolvedEntry>> => ({
  'native-image': {
    async resolve({ file, extension, format }) {
      const objectUrl = URL.createObjectURL(file)

      try {
        const dimensions = await inspectNativeImage(objectUrl)

        return {
          kind: 'image',
          file,
          extension,
          format,
          objectUrl,
          dimensions,
        }
      } catch (error) {
        URL.revokeObjectURL(objectUrl)
        throw error
      }
    },
  },
  deferred: {
    async resolve({ file, extension, format }) {
      return {
        kind: 'deferred',
        file,
        extension,
        format,
        headline: `${format.label} требует отдельный decode pipeline`,
        detail: format.notes,
        nextStep:
          'Следующий проход: backend adapter + единый preview contract для сложных image-форматов.',
      }
    },
  },
})

export function createViewerRuntime(
  dependencies: ViewerRuntimeDependencies = {},
): ViewerRuntime {
  const inspectNativeImage = dependencies.inspectNativeImage ?? defaultInspectNativeImage
  const strategies = previewStrategies(inspectNativeImage)

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
            'Нужно добавить definition в registry и назначить ему browser-native либо server-pipeline стратегию.',
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

function defaultInspectNativeImage(
  objectUrl: string,
): Promise<{ width: number; height: number }> {
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
