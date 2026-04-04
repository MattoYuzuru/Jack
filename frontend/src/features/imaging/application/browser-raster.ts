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

export interface RasterCanvasOptions {
  width?: number
  height?: number
  offsetX?: number
  offsetY?: number
  drawWidth?: number
  drawHeight?: number
  backgroundColor?: string
}

export interface RasterContainRect {
  width: number
  height: number
  offsetX: number
  offsetY: number
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
  const canvas = rasterFrameToCanvas(frame, {
    backgroundColor: options.mimeType === 'image/jpeg' ? options.backgroundColor ?? '#ffffff' : undefined,
  })

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
  const sourceCanvas = rasterFrameToCanvas(frame)
  const targetCanvas = createCanvasSurface(nextWidth, nextHeight, 'Canvas 2D context недоступен для resize-шага.')
  const targetContext = getCanvasContext(
    targetCanvas,
    'Canvas 2D context недоступен для resize-шага.',
  )

  // Resize идёт через отдельный canvas, чтобы все preset-профили проходили через один
  // и тот же downscale-путь до encode-стратегий и не дублировались по target-веткам.
  targetContext.drawImage(sourceCanvas, 0, 0, nextWidth, nextHeight)

  return canvasToRasterFrame(targetCanvas)
}

export function rasterFrameToCanvas(
  frame: RasterImageFrame,
  options: RasterCanvasOptions = {},
): HTMLCanvasElement {
  const canvas = createCanvasSurface(
    options.width ?? frame.width,
    options.height ?? frame.height,
    'Canvas 2D context недоступен для сборки raster layer.',
  )
  const context = getCanvasContext(canvas, 'Canvas 2D context недоступен для сборки raster layer.')

  if (options.backgroundColor) {
    context.fillStyle = options.backgroundColor
    context.fillRect(0, 0, canvas.width, canvas.height)
  }

  const sourceCanvas = createCanvasSurface(
    frame.width,
    frame.height,
    'Canvas 2D context недоступен для промежуточного raster layer.',
  )
  const sourceContext = getCanvasContext(
    sourceCanvas,
    'Canvas 2D context недоступен для промежуточного raster layer.',
  )

  sourceContext.putImageData(frame.imageData, 0, 0)

  context.drawImage(
    sourceCanvas,
    options.offsetX ?? 0,
    options.offsetY ?? 0,
    options.drawWidth ?? frame.width,
    options.drawHeight ?? frame.height,
  )

  return canvas
}

export function canvasToRasterFrame(canvas: HTMLCanvasElement): RasterImageFrame {
  const context = getCanvasContext(
    canvas,
    'Canvas 2D context недоступен для чтения raster frame из canvas.',
  )

  return createRasterFrame(context.getImageData(0, 0, canvas.width, canvas.height))
}

export function createRasterFrame(imageData: ImageData): RasterImageFrame {
  return {
    width: imageData.width,
    height: imageData.height,
    imageData,
    hasTransparency: detectTransparency(imageData),
  }
}

export function resolveContainRect(
  sourceWidth: number,
  sourceHeight: number,
  targetWidth: number,
  targetHeight: number,
): RasterContainRect {
  const scale = Math.min(targetWidth / sourceWidth, targetHeight / sourceHeight)
  const width = Math.max(1, Math.round(sourceWidth * scale))
  const height = Math.max(1, Math.round(sourceHeight * scale))

  return {
    width,
    height,
    offsetX: Math.floor((targetWidth - width) / 2),
    offsetY: Math.floor((targetHeight - height) / 2),
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

function createCanvasSurface(
  width: number,
  height: number,
  errorMessage: string,
): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  // Защищаемся единым guard'ом: все encode/decode helper'ы зависят от
  // доступности 2D-контекста, поэтому не размазываем эту проверку по модулям.
  getCanvasContext(canvas, errorMessage)

  return canvas
}

function getCanvasContext(canvas: HTMLCanvasElement, errorMessage: string): CanvasRenderingContext2D {
  const context = canvas.getContext('2d')

  if (!context) {
    throw new Error(errorMessage)
  }

  return context
}
