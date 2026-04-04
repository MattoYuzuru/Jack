export interface RasterImageFrame {
  width: number
  height: number
  imageData: ImageData
  hasTransparency: boolean
}

export interface RasterEncodeOptions {
  mimeType: string
  quality?: number
  backgroundColor?: string
}

export async function rasterizeBlob(blob: Blob): Promise<RasterImageFrame> {
  const objectUrl = URL.createObjectURL(blob)

  try {
    const image = await loadImage(objectUrl)
    const canvas = document.createElement('canvas')
    canvas.width = image.naturalWidth
    canvas.height = image.naturalHeight

    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('Canvas 2D context недоступен для raster decode.')
    }

    context.drawImage(image, 0, 0)
    const imageData = context.getImageData(0, 0, canvas.width, canvas.height)

    return {
      width: canvas.width,
      height: canvas.height,
      imageData,
      hasTransparency: detectTransparency(imageData),
    }
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}

export function encodeRasterFrame(
  frame: RasterImageFrame,
  options: RasterEncodeOptions,
): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = frame.width
  canvas.height = frame.height

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('Canvas 2D context недоступен для encode-шага.')
  }

  const sourceCanvas = document.createElement('canvas')
  sourceCanvas.width = frame.width
  sourceCanvas.height = frame.height

  const sourceContext = sourceCanvas.getContext('2d')
  if (!sourceContext) {
    throw new Error('Canvas 2D context недоступен для промежуточного raster layer.')
  }

  sourceContext.putImageData(frame.imageData, 0, 0)

  // JPEG не умеет хранить alpha-канал, поэтому для прозрачных источников
  // заранее подстилаем фон и только потом композим исходный raster.
  if (options.mimeType === 'image/jpeg') {
    context.fillStyle = options.backgroundColor ?? '#ffffff'
    context.fillRect(0, 0, frame.width, frame.height)
    context.drawImage(sourceCanvas, 0, 0)
  } else {
    context.drawImage(sourceCanvas, 0, 0)
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('Не удалось собрать итоговый файл из raster frame.'))
          return
        }

        if (blob.type !== options.mimeType) {
          reject(new Error(`Браузер не поддержал encode в ${options.mimeType}.`))
          return
        }

        resolve(blob)
      },
      options.mimeType,
      options.quality,
    )
  })
}

function loadImage(objectUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()

    image.onload = () => {
      if (!image.naturalWidth || !image.naturalHeight) {
        reject(new Error('Изображение загружено, но не вернуло размерность для raster decode.'))
        return
      }

      resolve(image)
    }

    image.onerror = () => {
      reject(new Error('Не удалось rasterize выбранный источник через browser image pipeline.'))
    }

    image.src = objectUrl
  })
}

function detectTransparency(imageData: ImageData): boolean {
  const data = imageData.data

  for (let index = 3; index < data.length; index += 4) {
    if ((data[index] ?? 255) < 255) {
      return true
    }
  }

  return false
}
