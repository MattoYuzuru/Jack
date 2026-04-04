import * as UTIF from 'utif2'
import { encodeRasterFrame, type RasterImageFrame } from './browser-raster'

export async function buildTiffFromRaster(raster: RasterImageFrame): Promise<Blob> {
  const buffer = UTIF.encodeImage(
    new Uint8Array(raster.imageData.data.buffer.slice(0)),
    raster.width,
    raster.height,
  )

  return new Blob([toArrayBuffer(new Uint8Array(buffer))], {
    type: 'image/tiff',
  })
}

export async function buildTiffPreviewFromRaster(raster: RasterImageFrame): Promise<Blob> {
  return encodeRasterFrame(raster, {
    mimeType: 'image/png',
  })
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}
