import {
  encodeRasterFrame,
  rasterizeBlob,
  resizeRasterFrame,
  type RasterImageFrame,
} from '../../imaging/application/browser-raster'
import {
  runServerImageConvert,
  type ServerImageConvertResult,
} from './converter-server-runtime'
import { resolveConverterPreset, type ConverterPresetDefinition } from '../domain/converter-presets'
import {
  detectConverterExtension,
  listConverterScenariosByFamily,
  listConverterTargetsForSource,
  resolveConverterScenario,
  resolveConverterSourceFormat,
  resolveConverterTargetFormat,
  type ConverterScenarioDefinition,
  type ConverterSourceFormatDefinition,
  type ConverterSourceStrategyId,
  type ConverterTargetFormatDefinition,
  type ConverterTargetStrategyId,
} from '../domain/converter-registry'

export interface ConverterPreparedSource {
  file: File
  extension: string
  source: ConverterSourceFormatDefinition
  targets: ConverterTargetFormatDefinition[]
  scenarios: ConverterScenarioDefinition[]
}

export interface ConverterResult {
  kind: 'image' | 'document'
  fileName: string
  blob: Blob
  previewBlob: Blob
  previewMimeType: string
  preset: ConverterPresetDefinition
  source: ConverterSourceFormatDefinition
  target: ConverterTargetFormatDefinition
  scenario: ConverterScenarioDefinition
  sourceWidth: number
  sourceHeight: number
  width: number
  height: number
  warnings: string[]
  backendJobId: string | null
  backendCompletedAt: string | null
  backendRuntimeLabel: string | null
}

export interface ConverterRuntime {
  inspect(file: File): Promise<ConverterPreparedSource | null>
  convert(input: {
    prepared: ConverterPreparedSource
    targetExtension: string
    presetId?: string
    quality?: number
    backgroundColor?: string
    onProgress?: (message: string) => void
    onJobCreated?: (jobId: string) => void
    onJobUpdate?: (job: {
      id: string
      status: string
      progressPercent: number
      message: string
      errorMessage: string | null
    }) => void
  }): Promise<ConverterResult>
}

interface ConverterDecodedSourceArtifact {
  raster: RasterImageFrame
  warnings: string[]
}

interface ConverterSourceStrategy {
  decode(prepared: ConverterPreparedSource): Promise<ConverterDecodedSourceArtifact>
}

interface ConverterTargetStrategy {
  encode(input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }): Promise<ConverterEncodedArtifact>
}

interface ConverterEncodedArtifact {
  blob: Blob
  previewBlob?: Blob
  previewMimeType?: string
  warnings: string[]
}

export interface ConverterRuntimeDependencies {
  decodeNativeRaster?: (prepared: ConverterPreparedSource) => Promise<ConverterDecodedSourceArtifact>
  decodeHeicSource?: (prepared: ConverterPreparedSource) => Promise<ConverterDecodedSourceArtifact>
  decodeTiffSource?: (prepared: ConverterPreparedSource) => Promise<ConverterDecodedSourceArtifact>
  decodeRawSource?: (prepared: ConverterPreparedSource) => Promise<ConverterDecodedSourceArtifact>
  decodePsdSource?: (prepared: ConverterPreparedSource) => Promise<ConverterDecodedSourceArtifact>
  decodeIllustrationSource?: (
    prepared: ConverterPreparedSource,
  ) => Promise<ConverterDecodedSourceArtifact>
  resizeRaster?: (
    raster: RasterImageFrame,
    preset: ConverterPresetDefinition,
  ) => Promise<RasterTransformArtifact>
  encodeJpeg?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodePng?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodeWebp?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodePdf?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodeTiff?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodeAvif?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodeSvg?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  encodeIco?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<ConverterEncodedArtifact>
  isServerScenario?: (
    prepared: ConverterPreparedSource,
    target: ConverterTargetFormatDefinition,
    scenario: ConverterScenarioDefinition,
  ) => boolean
  convertServerScenario?: (input: {
    prepared: ConverterPreparedSource
    target: ConverterTargetFormatDefinition
    preset: ConverterPresetDefinition
    quality?: number
    backgroundColor?: string
    onProgress?: (message: string) => void
    onJobCreated?: (jobId: string) => void
    onJobUpdate?: (job: {
      id: string
      status: string
      progressPercent: number
      message: string
      errorMessage: string | null
    }) => void
  }) => Promise<ServerImageConvertResult>
}

interface RasterTransformArtifact {
  raster: RasterImageFrame
  warnings: string[]
}

export function createConverterRuntime(
  dependencies: ConverterRuntimeDependencies = {},
): ConverterRuntime {
  const sourceStrategies = createSourceStrategies(dependencies)
  const targetStrategies = createTargetStrategies(dependencies)
  const applyPreset = dependencies.resizeRaster ?? defaultApplyPreset
  const isServerScenario = dependencies.isServerScenario ?? defaultIsServerScenario
  const convertServerScenario = dependencies.convertServerScenario ?? defaultConvertServerScenario

  return {
    async inspect(file) {
      const source = await resolveConverterSourceFormat(file.name, file.type)
      if (!source) {
        return null
      }

      if (!source.available) {
        throw new Error(
          source.availabilityDetail ||
            `Источник ${source.label} сейчас отключён в backend capability matrix.`,
        )
      }

      const targets = await listConverterTargetsForSource(file.name, file.type)
      const scenarios = (await Promise.all([
        listConverterScenariosByFamily('image'),
        listConverterScenariosByFamily('document'),
      ]))
        .flat()
        .filter((scenario) => scenario.sourceExtension === source.extension && scenario.available)

      return {
        file,
        extension: detectConverterExtension(file.name),
        source,
        targets,
        scenarios,
      }
    },

    async convert({
      prepared,
      targetExtension,
      presetId,
      quality,
      backgroundColor,
      onProgress,
      onJobCreated,
      onJobUpdate,
    }) {
      const target =
        prepared.targets.find((candidate) => candidate.extension === targetExtension) ??
        (await resolveConverterTargetFormat(targetExtension))
      const scenario =
        prepared.scenarios.find((candidate) => candidate.targetExtension === targetExtension) ??
        (await resolveConverterScenario(prepared.source.extension, targetExtension))
      const preset = await resolveConverterPreset(presetId)

      if (!target || !scenario) {
        throw new Error('Для выбранной пары source/target сценарий пока не зарегистрирован.')
      }

      if (!target.available) {
        throw new Error(
          target.availabilityDetail ||
            `Target ${target.label} сейчас отключён в backend capability matrix.`,
        )
      }

      if (!scenario.available) {
        throw new Error(
          scenario.availabilityDetail ||
            `Сценарий ${scenario.label} сейчас отключён в backend capability matrix.`,
        )
      }

      if (isServerScenario(prepared, target, scenario)) {
        const serverResult = await convertServerScenario({
          prepared,
          target,
          preset,
          quality: target.supportsQuality
            ? (quality ?? preset.preferredQuality ?? target.defaultQuality ?? undefined)
            : undefined,
          backgroundColor: backgroundColor ?? preset.defaultBackgroundColor ?? undefined,
          onProgress,
          onJobCreated,
          onJobUpdate,
        })

        return {
          kind: target.family === 'document' ? 'document' : 'image',
          fileName: replaceExtension(prepared.file.name, target.extension),
          blob: serverResult.resultBlob,
          previewBlob: serverResult.previewBlob,
          previewMimeType:
            serverResult.manifest.previewMediaType ||
            serverResult.previewBlob.type ||
            target.mimeType,
          preset,
          source: prepared.source,
          target,
          scenario,
          sourceWidth: serverResult.manifest.sourceWidth,
          sourceHeight: serverResult.manifest.sourceHeight,
          width: serverResult.manifest.width,
          height: serverResult.manifest.height,
          warnings: serverResult.manifest.warnings,
          backendJobId: serverResult.job.id,
          backendCompletedAt: serverResult.job.completedAt,
          backendRuntimeLabel: serverResult.manifest.runtimeLabel,
        }
      }

      const decoded = await sourceStrategies[prepared.source.sourceStrategyId].decode(prepared)
      const transformed = await applyPreset(decoded.raster, preset)
      const encoded = await targetStrategies[target.targetStrategyId].encode({
        raster: transformed.raster,
        target,
        quality: target.supportsQuality
          ? (quality ?? preset.preferredQuality ?? target.defaultQuality ?? undefined)
          : undefined,
        backgroundColor: backgroundColor ?? preset.defaultBackgroundColor ?? undefined,
      })

      const warnings = [...decoded.warnings, ...transformed.warnings, ...encoded.warnings]

      if (!target.supportsTransparency && transformed.raster.hasTransparency) {
        warnings.unshift(
          target.family === 'document'
            ? `Прозрачные области переведены в сплошной фон перед сборкой ${target.label}.`
            : `Прозрачные области переведены в сплошной фон перед ${target.label} encode.`,
        )
      }

      return {
        kind: target.family === 'document' ? 'document' : 'image',
        fileName: replaceExtension(prepared.file.name, target.extension),
        blob: encoded.blob,
        previewBlob: encoded.previewBlob ?? encoded.blob,
        previewMimeType: encoded.previewMimeType ?? target.mimeType,
        preset,
        source: prepared.source,
        target,
        scenario,
        sourceWidth: decoded.raster.width,
        sourceHeight: decoded.raster.height,
        width: transformed.raster.width,
        height: transformed.raster.height,
        warnings,
        backendJobId: null,
        backendCompletedAt: null,
        backendRuntimeLabel: null,
      }
    },
  }
}

function createSourceStrategies(
  dependencies: ConverterRuntimeDependencies,
): Record<ConverterSourceStrategyId, ConverterSourceStrategy> {
  return {
    'native-raster': {
      decode: dependencies.decodeNativeRaster ?? defaultDecodeNativeRaster,
    },
    'heic-raster': {
      decode: dependencies.decodeHeicSource ?? defaultDecodeHeicSource,
    },
    'tiff-raster': {
      decode: dependencies.decodeTiffSource ?? defaultDecodeTiffSource,
    },
    'raw-raster': {
      decode: dependencies.decodeRawSource ?? defaultDecodeRawSource,
    },
    'psd-raster': {
      decode: dependencies.decodePsdSource ?? defaultDecodePsdSource,
    },
    'illustration-raster': {
      decode: dependencies.decodeIllustrationSource ?? defaultDecodeIllustrationSource,
    },
  }
}

function createTargetStrategies(
  dependencies: ConverterRuntimeDependencies,
): Record<ConverterTargetStrategyId, ConverterTargetStrategy> {
  return {
    'jpeg-encoder': {
      encode: dependencies.encodeJpeg ?? defaultEncodeJpeg,
    },
    'png-encoder': {
      encode: dependencies.encodePng ?? defaultEncodePng,
    },
    'webp-encoder': {
      encode: dependencies.encodeWebp ?? defaultEncodeWebp,
    },
    'pdf-document': {
      encode: dependencies.encodePdf ?? defaultEncodePdf,
    },
    'tiff-image': {
      encode: dependencies.encodeTiff ?? defaultEncodeTiff,
    },
    'avif-encoder': {
      encode: dependencies.encodeAvif ?? defaultEncodeAvif,
    },
    'svg-vectorizer': {
      encode: dependencies.encodeSvg ?? defaultEncodeSvg,
    },
    'ico-image': {
      encode: dependencies.encodeIco ?? defaultEncodeIco,
    },
  }
}

async function defaultDecodeNativeRaster(
  prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  return {
    raster: await rasterizeBlob(prepared.file),
    warnings: [],
  }
}

async function defaultDecodeHeicSource(
  _prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  throw new Error('HEIC local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultDecodeTiffSource(
  _prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  throw new Error('TIFF local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultDecodeRawSource(
  _prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  throw new Error('RAW local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultDecodePsdSource(
  _prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  throw new Error('PSD local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultDecodeIllustrationSource(
  _prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  throw new Error('AI/EPS local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultApplyPreset(
  raster: RasterImageFrame,
  preset: ConverterPresetDefinition,
): Promise<RasterTransformArtifact> {
  const resized = resizeRasterFrame(raster, {
    maxWidth: preset.maxWidth,
    maxHeight: preset.maxHeight,
  })

  if (resized.width === raster.width && resized.height === raster.height) {
    return {
      raster: resized,
      warnings: [],
    }
  }

  return {
    raster: resized,
    warnings: [
      `Preset ${preset.label} уменьшил размерность: ${raster.width}x${raster.height} -> ${resized.width}x${resized.height}.`,
    ],
  }
}

async function defaultEncodeJpeg(input: {
  raster: RasterImageFrame
  quality?: number
  backgroundColor?: string
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await encodeRasterFrame(input.raster, {
      mimeType: 'image/jpeg',
      quality: input.quality,
      backgroundColor: input.backgroundColor,
    }),
    previewMimeType: 'image/jpeg',
    warnings: [],
  }
}

async function defaultEncodePng(input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await encodeRasterFrame(input.raster, {
      mimeType: 'image/png',
    }),
    previewMimeType: 'image/png',
    warnings: [],
  }
}

async function defaultEncodeWebp(input: {
  raster: RasterImageFrame
  quality?: number
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await encodeRasterFrame(input.raster, {
      mimeType: 'image/webp',
      quality: input.quality,
    }),
    previewMimeType: 'image/webp',
    warnings: [],
  }
}

async function defaultEncodePdf(_input: {
  raster: RasterImageFrame
  quality?: number
  backgroundColor?: string
}): Promise<ConverterEncodedArtifact> {
  throw new Error('PDF local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultEncodeTiff(_input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  throw new Error('TIFF local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultEncodeAvif(_input: {
  raster: RasterImageFrame
  quality?: number
}): Promise<ConverterEncodedArtifact> {
  throw new Error('AVIF local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultEncodeSvg(_input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  throw new Error('SVG trace local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

async function defaultEncodeIco(_input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  throw new Error('ICO local fallback отключён: heavy imaging должен идти через backend IMAGE_CONVERT.')
}

function defaultIsServerScenario(
  _prepared: ConverterPreparedSource,
  _target: ConverterTargetFormatDefinition,
  _scenario: ConverterScenarioDefinition,
): boolean {
  return true
}

async function defaultConvertServerScenario(input: {
  prepared: ConverterPreparedSource
  target: ConverterTargetFormatDefinition
  preset: ConverterPresetDefinition
  quality?: number
  backgroundColor?: string
  onProgress?: (message: string) => void
  onJobCreated?: (jobId: string) => void
  onJobUpdate?: (job: {
    id: string
    status: string
    progressPercent: number
    message: string
    errorMessage: string | null
  }) => void
}): Promise<ServerImageConvertResult> {
  return runServerImageConvert({
    file: input.prepared.file,
    targetExtension: input.target.extension,
    maxWidth: input.preset.maxWidth,
    maxHeight: input.preset.maxHeight,
    quality: input.quality,
    backgroundColor: input.backgroundColor,
    presetLabel: input.preset.label,
    reportProgress: input.onProgress,
    onJobCreated(job) {
      input.onJobCreated?.(job.id)
    },
    onJobUpdate: input.onJobUpdate,
  })
}

function replaceExtension(fileName: string, targetExtension: string): string {
  const lastDotIndex = fileName.lastIndexOf('.')

  if (lastDotIndex === -1) {
    return `${fileName}.${targetExtension}`
  }

  return `${fileName.slice(0, lastDotIndex)}.${targetExtension}`
}
