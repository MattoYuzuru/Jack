import {
  canvasToRasterFrame,
  encodeRasterFrame,
  rasterFrameToCanvas,
  resolveContainRect,
  type RasterImageFrame,
} from './browser-raster'

const defaultIconSizes = [16, 32, 48, 64, 128, 256]

export async function buildIcoFromRaster(raster: RasterImageFrame): Promise<Blob> {
  const entries = await Promise.all(
    resolveIcoSizes(raster.width, raster.height).map(async (size) => {
      const pngBlob = await buildSquareIconPng(raster, size)
      return {
        size,
        bytes: new Uint8Array(await pngBlob.arrayBuffer()),
      }
    }),
  )

  const headerSize = 6 + entries.length * 16
  const totalSize = headerSize + entries.reduce((sum, entry) => sum + entry.bytes.length, 0)
  const bytes = new Uint8Array(totalSize)
  const view = new DataView(bytes.buffer)

  view.setUint16(0, 0, true)
  view.setUint16(2, 1, true)
  view.setUint16(4, entries.length, true)

  let directoryOffset = 6
  let payloadOffset = headerSize

  for (const entry of entries) {
    const widthByte = entry.size >= 256 ? 0 : entry.size
    const heightByte = entry.size >= 256 ? 0 : entry.size

    bytes[directoryOffset] = widthByte
    bytes[directoryOffset + 1] = heightByte
    bytes[directoryOffset + 2] = 0
    bytes[directoryOffset + 3] = 0
    view.setUint16(directoryOffset + 4, 1, true)
    view.setUint16(directoryOffset + 6, 32, true)
    view.setUint32(directoryOffset + 8, entry.bytes.length, true)
    view.setUint32(directoryOffset + 12, payloadOffset, true)

    bytes.set(entry.bytes, payloadOffset)

    directoryOffset += 16
    payloadOffset += entry.bytes.length
  }

  return new Blob([bytes.buffer], { type: 'image/x-icon' })
}

export async function buildIcoPreviewFromRaster(raster: RasterImageFrame): Promise<Blob> {
  const previewSize = resolveIcoSizes(raster.width, raster.height).slice(-1)[0] ?? 64
  return buildSquareIconPng(raster, previewSize)
}

function resolveIcoSizes(width: number, height: number): number[] {
  const maxDimension = Math.max(1, Math.min(256, Math.max(width, height)))
  const sizes = defaultIconSizes.filter((size) => size <= maxDimension)

  if (!sizes.length) {
    return [maxDimension]
  }

  return sizes.includes(maxDimension) ? sizes : [...sizes, maxDimension]
}

async function buildSquareIconPng(raster: RasterImageFrame, size: number): Promise<Blob> {
  const placement = resolveContainRect(raster.width, raster.height, size, size)
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('Canvas 2D context недоступен для сборки ICO preview.')
  }

  // ICO почти всегда ожидает квадратные размеры, поэтому прямоугольные исходники
  // аккуратно центрируются на прозрачной подложке вместо неявного crop/stretch.
  context.clearRect(0, 0, size, size)
  context.drawImage(
    rasterFrameToCanvas(raster),
    placement.offsetX,
    placement.offsetY,
    placement.width,
    placement.height,
  )

  return encodeRasterFrame(canvasToRasterFrame(canvas), {
    mimeType: 'image/png',
  })
}
