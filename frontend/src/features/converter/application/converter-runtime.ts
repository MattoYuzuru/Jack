import {
  decodeHeicRaster,
  decodeRawRaster,
  decodeTiffRaster,
  type BinaryImagePayload,
} from '../../imaging/application/image-raster-codecs'
import {
  buildAvifFromRaster,
  buildAvifPreviewFromRaster,
} from '../../imaging/application/avif-image'
import {
  encodeRasterFrame,
  rasterizeBlob,
  resizeRasterFrame,
  type RasterImageFrame,
} from '../../imaging/application/browser-raster'
import { buildIcoFromRaster, buildIcoPreviewFromRaster } from '../../imaging/application/ico-image'
import { decodeIllustrationRaster } from '../../imaging/application/illustration-raster'
import { buildSinglePagePdfFromRaster } from '../../imaging/application/pdf-document'
import { decodePsdCompositeRaster } from '../../imaging/application/psd-raster'
import {
  buildTiffFromRaster,
  buildTiffPreviewFromRaster,
} from '../../imaging/application/tiff-image'
import { buildVectorizedSvgFromRaster } from '../../imaging/application/vectorized-svg'
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
}

export interface ConverterRuntime {
  inspect(file: File): ConverterPreparedSource | null
  convert(input: {
    prepared: ConverterPreparedSource
    targetExtension: string
    presetId?: string
    quality?: number
    backgroundColor?: string
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

  return {
    inspect(file) {
      const source = resolveConverterSourceFormat(file.name, file.type)
      if (!source) {
        return null
      }

      const targets = listConverterTargetsForSource(file.name, file.type)
      const scenarios = [
        ...listConverterScenariosByFamily('image'),
        ...listConverterScenariosByFamily('document'),
      ].filter((scenario) => scenario.sourceExtension === source.extension)

      return {
        file,
        extension: detectConverterExtension(file.name),
        source,
        targets,
        scenarios,
      }
    },

    async convert({ prepared, targetExtension, presetId, quality, backgroundColor }) {
      const target = resolveConverterTargetFormat(targetExtension)
      const scenario = resolveConverterScenario(prepared.source.extension, targetExtension)
      const preset = resolveConverterPreset(presetId)

      if (!target || !scenario) {
        throw new Error('Для выбранной пары source/target сценарий пока не зарегистрирован.')
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
  prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  return {
    raster: await rasterizeBinaryPayload(await decodeBinaryFile(prepared.file, decodeHeicRaster)),
    warnings: [],
  }
}

async function defaultDecodeTiffSource(
  prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  return {
    raster: await rasterizeBinaryPayload(await decodeBinaryFile(prepared.file, decodeTiffRaster)),
    warnings: [],
  }
}

async function defaultDecodeRawSource(
  prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  return {
    raster: await rasterizeBinaryPayload(await decodeBinaryFile(prepared.file, decodeRawRaster)),
    warnings: [],
  }
}

async function defaultDecodePsdSource(
  prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  return decodePsdCompositeRaster(prepared.file)
}

async function defaultDecodeIllustrationSource(
  prepared: ConverterPreparedSource,
): Promise<ConverterDecodedSourceArtifact> {
  return decodeIllustrationRaster(prepared.file)
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

async function defaultEncodePdf(input: {
  raster: RasterImageFrame
  quality?: number
  backgroundColor?: string
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await buildSinglePagePdfFromRaster(input.raster, {
      quality: input.quality,
      backgroundColor: input.backgroundColor,
    }),
    previewMimeType: 'application/pdf',
    warnings: [
      'PDF собран как single-page raster document без отдельного текстового или векторного слоя.',
    ],
  }
}

async function defaultEncodeTiff(input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await buildTiffFromRaster(input.raster),
    previewBlob: await buildTiffPreviewFromRaster(input.raster),
    previewMimeType: 'image/png',
    warnings: [
      'TIFF собран как single-frame RGBA image без multi-page контейнера и без исходных metadata-блоков.',
    ],
  }
}

async function defaultEncodeAvif(input: {
  raster: RasterImageFrame
  quality?: number
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await buildAvifFromRaster(input.raster, {
      quality: input.quality,
    }),
    previewBlob: await buildAvifPreviewFromRaster(input.raster),
    previewMimeType: 'image/png',
    warnings: [],
  }
}

async function defaultEncodeSvg(input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  const blob = await buildVectorizedSvgFromRaster(input.raster)

  return {
    blob,
    previewMimeType: 'image/svg+xml',
    warnings: [
      'SVG target собран через bitmap tracing, поэтому итог остаётся approximation, а не исходной векторной сценой.',
    ],
  }
}

async function defaultEncodeIco(input: {
  raster: RasterImageFrame
}): Promise<ConverterEncodedArtifact> {
  return {
    blob: await buildIcoFromRaster(input.raster),
    previewBlob: await buildIcoPreviewFromRaster(input.raster),
    previewMimeType: 'image/png',
    warnings:
      input.raster.width === input.raster.height
        ? []
        : [
            'ICO target собран на квадратных canvas-слоях: исходник центрирован с прозрачными полями.',
          ],
  }
}

async function decodeBinaryFile(
  file: File,
  decoder: (buffer: ArrayBuffer) => Promise<BinaryImagePayload>,
): Promise<BinaryImagePayload> {
  return decoder(await file.arrayBuffer())
}

async function rasterizeBinaryPayload(payload: BinaryImagePayload): Promise<RasterImageFrame> {
  return rasterizeBlob(new Blob([toArrayBuffer(payload.bytes)], { type: payload.mimeType }))
}

function replaceExtension(fileName: string, targetExtension: string): string {
  const lastDotIndex = fileName.lastIndexOf('.')

  if (lastDotIndex === -1) {
    return `${fileName}.${targetExtension}`
  }

  return `${fileName.slice(0, lastDotIndex)}.${targetExtension}`
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}
