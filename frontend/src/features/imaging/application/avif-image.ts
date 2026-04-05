import type { EncodeOptions } from '@jsquash/avif/meta.js'
import { encodeRasterFrame, type RasterImageFrame } from './browser-raster'

type AvifEncoder = (
  data: ImageData,
  options?: Partial<EncodeOptions> & { bitDepth?: 8 },
) => Promise<ArrayBuffer>

let avifEncoderPromise: Promise<AvifEncoder> | null = null

export async function buildAvifFromRaster(
  raster: RasterImageFrame,
  options: {
    quality?: number
  } = {},
): Promise<Blob> {
  const encode = await loadAvifEncoder()
  const buffer = await encode(cloneImageData(raster.imageData), {
    quality: normalizeAvifQuality(options.quality),
    qualityAlpha: -1,
  })

  return new Blob([buffer], { type: 'image/avif' })
}

export async function buildAvifPreviewFromRaster(raster: RasterImageFrame): Promise<Blob> {
  return encodeRasterFrame(raster, {
    mimeType: 'image/png',
  })
}

async function loadAvifEncoder(): Promise<AvifEncoder> {
  avifEncoderPromise ??= import('@jsquash/avif').then((module) => module.encode)
  return avifEncoderPromise
}

function cloneImageData(imageData: ImageData): ImageData {
  return new ImageData(new Uint8ClampedArray(imageData.data), imageData.width, imageData.height)
}

function normalizeAvifQuality(quality = 0.78): number {
  return Math.max(1, Math.min(100, Math.round(quality * 100)))
}
