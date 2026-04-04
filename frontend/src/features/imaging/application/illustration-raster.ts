import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'
import { decodeTiffRaster } from './image-raster-codecs'
import {
  canvasToRasterFrame,
  createRasterFrame,
  rasterizeBlob,
  type RasterImageFrame,
} from './browser-raster'

interface IllustrationDecodeArtifact {
  raster: RasterImageFrame
  warnings: string[]
}

interface PdfJsModule {
  getDocument(input: { data: Uint8Array }): {
    promise: Promise<{
      numPages: number
      getPage(pageNumber: number): Promise<{
        getViewport(input: { scale: number }): { width: number; height: number }
        render(input: {
          canvasContext: CanvasRenderingContext2D
          viewport: { width: number; height: number }
        }): { promise: Promise<void> }
        cleanup(): void
      }>
      cleanup(): void
      destroy(): Promise<void>
    }>
    destroy(): Promise<void>
  }
  GlobalWorkerOptions: {
    workerSrc: string
  }
}

let pdfJsPromise: Promise<PdfJsModule> | null = null

export async function decodeIllustrationRaster(file: File): Promise<IllustrationDecodeArtifact> {
  const bytes = new Uint8Array(await file.arrayBuffer())

  // Для AI/EPS держим каскад адаптеров, а не один "магический" parser:
  // сначала пробуем полноценный PDF-compatible render, затем embedded preview.
  const pdfCompatible = await tryDecodePdfIllustration(bytes)
  if (pdfCompatible) {
    return pdfCompatible
  }

  const embeddedTiffPreview = await tryDecodeDosEpsTiffPreview(bytes)
  if (embeddedTiffPreview) {
    return embeddedTiffPreview
  }

  const epsiPreview = tryDecodeEpsiPreview(bytes)
  if (epsiPreview) {
    return epsiPreview
  }

  throw new Error(
    'AI/EPS adapter не нашёл ни PDF-compatible контент, ни embedded preview для browser-first decode.',
  )
}

async function tryDecodePdfIllustration(
  bytes: Uint8Array,
): Promise<IllustrationDecodeArtifact | null> {
  try {
    return await renderPdfBytesToRaster(bytes, [
      'AI/EPS сведен через PDF-compatible render path в единый raster-слой.',
    ])
  } catch {
    // Ignore and continue to embedded PDF / preview fallbacks.
  }

  const embeddedPdf = extractEmbeddedPdf(bytes)
  if (!embeddedPdf) {
    return null
  }

  try {
    return await renderPdfBytesToRaster(embeddedPdf, [
      'AI/EPS использует встроенный PDF-compatible слой и после него сводится в raster.',
    ])
  } catch {
    return null
  }
}

async function renderPdfBytesToRaster(
  bytes: Uint8Array,
  warnings: string[],
): Promise<IllustrationDecodeArtifact> {
  const pdfjs = await loadPdfJs()
  const loadingTask = pdfjs.getDocument({ data: bytes })
  const pdfDocument = await loadingTask.promise

  try {
    const page = await pdfDocument.getPage(1)
    const baseViewport = page.getViewport({ scale: 1 })
    const scale = resolveIllustrationRenderScale(baseViewport.width, baseViewport.height)
    const viewport = page.getViewport({ scale })
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.ceil(viewport.width))
    canvas.height = Math.max(1, Math.ceil(viewport.height))

    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('Canvas 2D context недоступен для AI/EPS PDF render path.')
    }

    await page.render({ canvasContext: context, viewport }).promise
    page.cleanup()

    return {
      raster: canvasToRasterFrame(canvas),
      warnings:
        pdfDocument.numPages > 1
          ? [...warnings, 'AI/EPS источник содержит несколько страниц, взята только первая.']
          : warnings,
    }
  } finally {
    pdfDocument.cleanup()
    await pdfDocument.destroy()
    await loadingTask.destroy()
  }
}

async function loadPdfJs(): Promise<PdfJsModule> {
  pdfJsPromise ??= import('pdfjs-dist').then((module) => {
    const candidate = module as unknown as PdfJsModule
    candidate.GlobalWorkerOptions.workerSrc = pdfWorkerUrl
    return candidate
  })

  return pdfJsPromise
}

async function tryDecodeDosEpsTiffPreview(
  bytes: Uint8Array,
): Promise<IllustrationDecodeArtifact | null> {
  if (!hasDosEpsHeader(bytes)) {
    return null
  }

  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const tiffOffset = view.getUint32(20, true)
  const tiffLength = view.getUint32(24, true)

  if (!tiffOffset || !tiffLength || tiffOffset + tiffLength > bytes.byteLength) {
    return null
  }

  const previewBytes = bytes.slice(tiffOffset, tiffOffset + tiffLength)
  const payload = await decodeTiffRaster(toArrayBuffer(previewBytes))
  const raster = await rasterizeBlob(
    new Blob([toArrayBuffer(payload.bytes)], {
      type: payload.mimeType,
    }),
  )

  return {
    raster,
    warnings: [
      'EPS использует embedded TIFF preview вместо интерпретации PostScript-кода.',
    ],
  }
}

function tryDecodeEpsiPreview(bytes: Uint8Array): IllustrationDecodeArtifact | null {
  const decoder = new TextDecoder('latin1')
  const content = decoder.decode(bytes)
  const match = content.match(/%%BeginPreview:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)/u)

  if (!match) {
    return null
  }

  const width = Number(match[1])
  const height = Number(match[2])
  const depth = Number(match[3])
  const previewStart = (match.index ?? 0) + match[0].length
  const previewEnd = content.indexOf('%%EndPreview', previewStart)

  if (!width || !height || previewEnd === -1) {
    return null
  }

  const previewHex = content
    .slice(previewStart, previewEnd)
    .split('\n')
    .map((line) => line.replace(/^%/u, '').replace(/[^0-9a-f]/giu, ''))
    .join('')

  if (!previewHex.length) {
    return null
  }

  const previewBytes = hexToBytes(previewHex)
  const imageData =
    depth === 1
      ? buildMonochromePreview(previewBytes, width, height)
      : buildGrayscalePreview(previewBytes, width, height)

  return {
    raster: createRasterFrame(imageData),
    warnings: [
      'EPS использует встроенный EPSI preview, поэтому итог ограничен низкоуровневым preview-слоем.',
    ],
  }
}

function extractEmbeddedPdf(bytes: Uint8Array): Uint8Array | null {
  const start = findAsciiMarker(bytes, '%PDF-')
  if (start === -1) {
    return null
  }

  const endMarker = '%%EOF'
  const end = findLastAsciiMarker(bytes, endMarker)

  if (end === -1 || end <= start) {
    return bytes.slice(start)
  }

  return bytes.slice(start, end + endMarker.length)
}

function hasDosEpsHeader(bytes: Uint8Array): boolean {
  return (
    bytes.byteLength >= 30 &&
    bytes[0] === 0xc5 &&
    bytes[1] === 0xd0 &&
    bytes[2] === 0xd3 &&
    bytes[3] === 0xc6
  )
}

function resolveIllustrationRenderScale(width: number, height: number): number {
  const longestSide = Math.max(width, height)

  if (!Number.isFinite(longestSide) || longestSide <= 0) {
    return 2
  }

  return Math.max(1.5, Math.min(3, 2400 / longestSide))
}

function buildMonochromePreview(bytes: Uint8Array, width: number, height: number): ImageData {
  const bytesPerRow = Math.ceil(width / 8)
  const rgba = new Uint8ClampedArray(width * height * 4)

  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const byte = bytes[y * bytesPerRow + Math.floor(x / 8)] ?? 0
      const bit = (byte >> (7 - (x % 8))) & 1
      const value = bit ? 0 : 255
      const offset = (y * width + x) * 4
      rgba[offset] = value
      rgba[offset + 1] = value
      rgba[offset + 2] = value
      rgba[offset + 3] = 255
    }
  }

  return new ImageData(rgba, width, height)
}

function buildGrayscalePreview(bytes: Uint8Array, width: number, height: number): ImageData {
  const rgba = new Uint8ClampedArray(width * height * 4)

  for (let index = 0; index < width * height; index += 1) {
    const value = bytes[index] ?? 255
    const offset = index * 4
    rgba[offset] = value
    rgba[offset + 1] = value
    rgba[offset + 2] = value
    rgba[offset + 3] = 255
  }

  return new ImageData(rgba, width, height)
}

function hexToBytes(value: string): Uint8Array {
  const normalized = value.length % 2 === 0 ? value : `${value}0`
  const output = new Uint8Array(normalized.length / 2)

  for (let index = 0; index < normalized.length; index += 2) {
    output[index / 2] = Number.parseInt(normalized.slice(index, index + 2), 16)
  }

  return output
}

function findAsciiMarker(bytes: Uint8Array, marker: string): number {
  const markerBytes = new TextEncoder().encode(marker)

  for (let offset = 0; offset <= bytes.length - markerBytes.length; offset += 1) {
    let matches = true

    for (let index = 0; index < markerBytes.length; index += 1) {
      if (bytes[offset + index] !== markerBytes[index]) {
        matches = false
        break
      }
    }

    if (matches) {
      return offset
    }
  }

  return -1
}

function findLastAsciiMarker(bytes: Uint8Array, marker: string): number {
  const markerBytes = new TextEncoder().encode(marker)

  for (let offset = bytes.length - markerBytes.length; offset >= 0; offset -= 1) {
    let matches = true

    for (let index = 0; index < markerBytes.length; index += 1) {
      if (bytes[offset + index] !== markerBytes[index]) {
        matches = false
        break
      }
    }

    if (matches) {
      return offset
    }
  }

  return -1
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}
