import * as UTIF from 'utif2'

export interface BinaryImagePayload {
  bytes: Uint8Array
  mimeType: string
}

type HeicConvert = (input: {
  blob: Blob
  toType?: string
  quality?: number
  multiple?: true
  gifInterval?: number
}) => Promise<Blob | Blob[]>

export async function decodeHeicRaster(buffer: ArrayBuffer): Promise<BinaryImagePayload> {
  const module = await import('heic2any')
  const convert = (module.default ?? module) as HeicConvert
  const result = await convert({
    blob: new Blob([buffer], { type: 'image/heic' }),
    toType: 'image/jpeg',
    quality: 0.92,
  })
  const blob = Array.isArray(result) ? result[0] : result

  if (!blob) {
    throw new Error('HEIC adapter не вернул raster preview.')
  }

  return {
    bytes: new Uint8Array(await blob.arrayBuffer()),
    mimeType: 'image/jpeg',
  }
}

export async function decodeTiffRaster(buffer: ArrayBuffer): Promise<BinaryImagePayload> {
  return decodeTiffLikeRaster(buffer)
}

export async function decodeRawRaster(buffer: ArrayBuffer): Promise<BinaryImagePayload> {
  return decodeTiffLikeRaster(buffer)
}

async function decodeTiffLikeRaster(buffer: ArrayBuffer): Promise<BinaryImagePayload> {
  const ifds = UTIF.decode(buffer)
  const candidates = [...ifds].sort((left, right) => scoreIfd(right) - scoreIfd(left))

  // Для TIFF/RAW нельзя доверять первому IFD: контейнер нередко хранит рядом
  // и сырые данные, и уже готовый preview. Поэтому перебираем все renderable кандидаты.
  for (const ifd of candidates) {
    try {
      UTIF.decodeImage(buffer, ifd)

      if (!ifd.width || !ifd.height) {
        continue
      }

      const rgba = UTIF.toRGBA8(ifd)
      if (rgba.length !== ifd.width * ifd.height * 4) {
        continue
      }

      return {
        bytes: await rgbaToPngBytes(rgba, ifd.width, ifd.height),
        mimeType: 'image/png',
      }
    } catch {
      continue
    }
  }

  throw new Error('Файл распознан, но в нём не найден renderable preview-слой.')
}

function scoreIfd(ifd: UTIF.IFD): number {
  const width = readTiffNumberTag(ifd, 256) ?? ifd.width ?? 0
  const height = readTiffNumberTag(ifd, 257) ?? ifd.height ?? 0
  const samples = readTiffNumberTag(ifd, 277) ?? 1

  return width * height * samples
}

function readTiffNumberTag(ifd: UTIF.IFD, tagId: number): number | null {
  const rawValue = ifd[`t${tagId}`]

  if (typeof rawValue === 'number') {
    return rawValue
  }

  if (Array.isArray(rawValue) && typeof rawValue[0] === 'number') {
    return rawValue[0]
  }

  return null
}

function rgbaToPngBytes(rgba: Uint8Array, width: number, height: number): Promise<Uint8Array> {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('Canvas 2D context недоступен для сборки preview.')
  }

  const imageData = new ImageData(new Uint8ClampedArray(rgba), width, height)
  context.putImageData(imageData, 0, 0)

  return new Promise((resolve, reject) => {
    canvas.toBlob(async (blob) => {
      if (!blob) {
        reject(new Error('Не удалось собрать PNG preview из raster buffer.'))
        return
      }

      const arrayBuffer = await blob.arrayBuffer()
      resolve(new Uint8Array(arrayBuffer))
    }, 'image/png')
  })
}
