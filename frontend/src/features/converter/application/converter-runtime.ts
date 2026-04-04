import {
  decodeHeicRaster,
  decodeRawRaster,
  decodeTiffRaster,
  type BinaryImagePayload,
} from '../../imaging/application/image-raster-codecs'
import {
  encodeRasterFrame,
  rasterizeBlob,
  type RasterImageFrame,
} from '../../imaging/application/browser-raster'
import { buildSinglePagePdfFromRaster } from '../../imaging/application/pdf-document'
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
  previewMimeType: string
  source: ConverterSourceFormatDefinition
  target: ConverterTargetFormatDefinition
  scenario: ConverterScenarioDefinition
  width: number
  height: number
  warnings: string[]
}

export interface ConverterRuntime {
  inspect(file: File): ConverterPreparedSource | null
  convert(input: {
    prepared: ConverterPreparedSource
    targetExtension: string
    quality?: number
    backgroundColor?: string
  }): Promise<ConverterResult>
}

interface ConverterSourceStrategy {
  decode(prepared: ConverterPreparedSource): Promise<RasterImageFrame>
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
  warnings: string[]
}

export interface ConverterRuntimeDependencies {
  decodeNativeRaster?: (prepared: ConverterPreparedSource) => Promise<RasterImageFrame>
  decodeHeicSource?: (prepared: ConverterPreparedSource) => Promise<RasterImageFrame>
  decodeTiffSource?: (prepared: ConverterPreparedSource) => Promise<RasterImageFrame>
  decodeRawSource?: (prepared: ConverterPreparedSource) => Promise<RasterImageFrame>
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
}

export function createConverterRuntime(
  dependencies: ConverterRuntimeDependencies = {},
): ConverterRuntime {
  const sourceStrategies = createSourceStrategies(dependencies)
  const targetStrategies = createTargetStrategies(dependencies)

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

    async convert({ prepared, targetExtension, quality, backgroundColor }) {
      const target = resolveConverterTargetFormat(targetExtension)
      const scenario = resolveConverterScenario(prepared.source.extension, targetExtension)

      if (!target || !scenario) {
        throw new Error('Для выбранной пары source/target сценарий пока не зарегистрирован.')
      }

      const raster = await sourceStrategies[prepared.source.sourceStrategyId].decode(prepared)
      const encoded = await targetStrategies[target.targetStrategyId].encode({
        raster,
        target,
        quality: target.supportsQuality
          ? (quality ?? target.defaultQuality ?? undefined)
          : undefined,
        backgroundColor,
      })

      const warnings = [...encoded.warnings]

      if (!target.supportsTransparency && raster.hasTransparency) {
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
        previewMimeType: target.mimeType,
        source: prepared.source,
        target,
        scenario,
        width: raster.width,
        height: raster.height,
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
  }
}

async function defaultDecodeNativeRaster(
  prepared: ConverterPreparedSource,
): Promise<RasterImageFrame> {
  return rasterizeBlob(prepared.file)
}

async function defaultDecodeHeicSource(
  prepared: ConverterPreparedSource,
): Promise<RasterImageFrame> {
  return rasterizeBinaryPayload(await decodeBinaryFile(prepared.file, decodeHeicRaster))
}

async function defaultDecodeTiffSource(
  prepared: ConverterPreparedSource,
): Promise<RasterImageFrame> {
  return rasterizeBinaryPayload(await decodeBinaryFile(prepared.file, decodeTiffRaster))
}

async function defaultDecodeRawSource(
  prepared: ConverterPreparedSource,
): Promise<RasterImageFrame> {
  return rasterizeBinaryPayload(await decodeBinaryFile(prepared.file, decodeRawRaster))
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
    warnings: [
      'PDF собран как single-page raster document без отдельного текстового или векторного слоя.',
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
  return rasterizeBlob(new Blob([payload.bytes.slice().buffer], { type: payload.mimeType }))
}

function replaceExtension(fileName: string, targetExtension: string): string {
  const lastDotIndex = fileName.lastIndexOf('.')

  if (lastDotIndex === -1) {
    return `${fileName}.${targetExtension}`
  }

  return `${fileName.slice(0, lastDotIndex)}.${targetExtension}`
}
