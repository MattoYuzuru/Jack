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

export interface RasterResizeOptions {
  maxWidth?: number | null
  maxHeight?: number | null
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

export function resizeRasterFrame(
  frame: RasterImageFrame,
  options: RasterResizeOptions,
): RasterImageFrame {
  const maxWidth = options.maxWidth ?? null
  const maxHeight = options.maxHeight ?? null

  if (!maxWidth && !maxHeight) {
    return frame
  }

  const widthScale = maxWidth ? maxWidth / frame.width : 1
  const heightScale = maxHeight ? maxHeight / frame.height : 1
  const scale = Math.min(1, widthScale, heightScale)

  if (scale === 1) {
    return frame
  }

  const nextWidth = Math.max(1, Math.round(frame.width * scale))
  const nextHeight = Math.max(1, Math.round(frame.height * scale))

  const sourceCanvas = document.createElement('canvas')
  sourceCanvas.width = frame.width
  sourceCanvas.height = frame.height

  const sourceContext = sourceCanvas.getContext('2d')
  if (!sourceContext) {
    throw new Error('Canvas 2D context недоступен для подготовки resize-источника.')
  }

  sourceContext.putImageData(frame.imageData, 0, 0)

  const targetCanvas = document.createElement('canvas')
  targetCanvas.width = nextWidth
  targetCanvas.height = nextHeight

  const targetContext = targetCanvas.getContext('2d')
  if (!targetContext) {
    throw new Error('Canvas 2D context недоступен для resize-шага.')
  }

  // Resize идёт через отдельный canvas, чтобы все preset-профили проходили через один
  // и тот же downscale-путь до encode-стратегий и не дублировались по target-веткам.
  targetContext.drawImage(sourceCanvas, 0, 0, nextWidth, nextHeight)

  const imageData = targetContext.getImageData(0, 0, nextWidth, nextHeight)

  return {
    width: nextWidth,
    height: nextHeight,
    imageData,
    hasTransparency: detectTransparency(imageData),
  }
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
