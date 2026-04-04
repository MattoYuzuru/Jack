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
  fileName: string
  blob: Blob
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
  }): Promise<Blob>
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
  }) => Promise<Blob>
  encodePng?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<Blob>
  encodeWebp?: (input: {
    raster: RasterImageFrame
    target: ConverterTargetFormatDefinition
    quality?: number
    backgroundColor?: string
  }) => Promise<Blob>
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
      const scenarios = listConverterScenariosByFamily(source.family).filter(
        (scenario) => scenario.sourceExtension === source.extension,
      )

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
      const blob = await targetStrategies[target.targetStrategyId].encode({
        raster,
        target,
        quality: target.supportsQuality
          ? (quality ?? target.defaultQuality ?? undefined)
          : undefined,
        backgroundColor,
      })

      const warnings =
        target.extension === 'jpg' && raster.hasTransparency
          ? ['Прозрачные области переведены в сплошной фон перед JPEG encode.']
          : []

      return {
        fileName: replaceExtension(prepared.file.name, target.extension),
        blob,
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
}): Promise<Blob> {
  return encodeRasterFrame(input.raster, {
    mimeType: 'image/jpeg',
    quality: input.quality,
    backgroundColor: input.backgroundColor,
  })
}

async function defaultEncodePng(input: { raster: RasterImageFrame }): Promise<Blob> {
  return encodeRasterFrame(input.raster, {
    mimeType: 'image/png',
  })
}

async function defaultEncodeWebp(input: {
  raster: RasterImageFrame
  quality?: number
}): Promise<Blob> {
  return encodeRasterFrame(input.raster, {
    mimeType: 'image/webp',
    quality: input.quality,
  })
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
