import { encodeRasterFrame, type RasterImageFrame } from './browser-raster'

export interface PdfDocumentBuildOptions {
  quality?: number
  backgroundColor?: string
}

export async function buildSinglePagePdfFromRaster(
  raster: RasterImageFrame,
  options: PdfDocumentBuildOptions = {},
): Promise<Blob> {
  const jpegBlob = await encodeRasterFrame(raster, {
    mimeType: 'image/jpeg',
    quality: options.quality ?? 0.92,
    backgroundColor: options.backgroundColor ?? '#ffffff',
  })
  const jpegBytes = new Uint8Array(await jpegBlob.arrayBuffer())

  // Для первой PDF-итерации сохраняем page box один-в-один с raster dimensions,
  // чтобы не плодить второй слой масштабирования поверх уже готового canvas pipeline.
  return new Blob([toArrayBuffer(assemblePdfDocument(jpegBytes, raster.width, raster.height))], {
    type: 'application/pdf',
  })
}

function assemblePdfDocument(jpegBytes: Uint8Array, width: number, height: number): Uint8Array {
  const encoder = new TextEncoder()
  const parts: Uint8Array[] = []
  const offsets: number[] = [0]
  let position = 0

  const header = encoder.encode('%PDF-1.4\n%\xFF\xFF\xFF\xFF\n')
  parts.push(header)
  position += header.length

  const imageObjectIndex = 5
  const contentStream = encoder.encode(`q\n${width} 0 0 ${height} 0 0 cm\n/Im0 Do\nQ\n`)
  const imageObjectHeader = encoder.encode(
    `5 0 obj\n<< /Type /XObject /Subtype /Image /Width ${width} /Height ${height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.length} >>\nstream\n`,
  )
  const imageObjectFooter = encoder.encode('\nendstream\nendobj\n')

  const objects = [
    encoder.encode('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n'),
    encoder.encode('2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n'),
    encoder.encode(
      `3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${formatPdfNumber(width)} ${formatPdfNumber(height)}] /Resources << /XObject << /Im0 ${imageObjectIndex} 0 R >> >> /Contents 4 0 R >>\nendobj\n`,
    ),
    encoder.encode(
      `4 0 obj\n<< /Length ${contentStream.length} >>\nstream\n${decoderless(contentStream)}endstream\nendobj\n`,
    ),
  ]

  for (const objectBytes of objects) {
    offsets.push(position)
    parts.push(objectBytes)
    position += objectBytes.length
  }

  offsets.push(position)
  parts.push(imageObjectHeader)
  parts.push(jpegBytes)
  parts.push(imageObjectFooter)
  position += imageObjectHeader.length + jpegBytes.length + imageObjectFooter.length

  const xrefOffset = position
  const xrefHeader = encoder.encode(`xref\n0 ${offsets.length}\n0000000000 65535 f \n`)
  parts.push(xrefHeader)

  for (const offset of offsets.slice(1)) {
    parts.push(encoder.encode(`${offset.toString().padStart(10, '0')} 00000 n \n`))
  }

  parts.push(
    encoder.encode(
      `trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`,
    ),
  )

  return concatenatePdfParts(parts)
}

function concatenatePdfParts(parts: Uint8Array[]): Uint8Array {
  const totalLength = parts.reduce((sum, part) => sum + part.length, 0)
  const output = new Uint8Array(totalLength)
  let offset = 0

  for (const part of parts) {
    output.set(part, offset)
    offset += part.length
  }

  return output
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}

function formatPdfNumber(value: number): string {
  return Number(value.toFixed(2)).toString()
}

function decoderless(bytes: Uint8Array): string {
  return String.fromCharCode(...bytes)
}
