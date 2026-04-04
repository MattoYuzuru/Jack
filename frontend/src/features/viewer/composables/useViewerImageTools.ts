import { computed, ref, watch, type Ref } from 'vue'
import type { ViewerResolvedEntry } from '../application/viewer-runtime'
import {
  computeHistogram,
  rgbaToHex,
  rgbaToHslString,
  rgbaToRgbString,
  type ViewerColorValue,
  type ViewerHistogram,
} from '../application/viewer-color-tools'

export interface ViewerColorSample {
  x: number
  y: number
  color: ViewerColorValue
  hex: string
  rgb: string
  hsl: string
  alpha: string
  loupeDataUrl: string
}

export interface ViewerSavedSwatch {
  id: number
  sample: ViewerColorSample
}

export function useViewerImageTools(
  selection: Ref<ViewerResolvedEntry | null>,
  imageElement: Ref<HTMLImageElement | null>,
) {
  const analysisCanvas = typeof document !== 'undefined' ? document.createElement('canvas') : null
  const loupeCanvas = typeof document !== 'undefined' ? document.createElement('canvas') : null
  const scratchCanvas = typeof document !== 'undefined' ? document.createElement('canvas') : null

  const activeSample = ref<ViewerColorSample | null>(null)
  const histogram = ref<ViewerHistogram | null>(null)
  const swatches = ref<ViewerSavedSwatch[]>([])
  const isTransparencyGridVisible = ref(true)

  let swatchId = 0

  watch(
    () => (selection.value?.kind === 'image' ? selection.value.objectUrl : ''),
    async (objectUrl) => {
      activeSample.value = null
      histogram.value = null
      swatches.value = []

      if (!objectUrl || !analysisCanvas) {
        return
      }

      const image = await loadImage(objectUrl)
      const analysisSize = createAnalysisSize(image.naturalWidth, image.naturalHeight)

      analysisCanvas.width = analysisSize.width
      analysisCanvas.height = analysisSize.height

      const context = analysisCanvas.getContext('2d', {
        willReadFrequently: true,
      })

      if (!context) {
        return
      }

      context.clearRect(0, 0, analysisSize.width, analysisSize.height)
      context.drawImage(image, 0, 0, analysisSize.width, analysisSize.height)

      histogram.value = computeHistogram(
        context.getImageData(0, 0, analysisSize.width, analysisSize.height).data,
      )
    },
    { immediate: true },
  )

  const canUseTools = computed(() => selection.value?.kind === 'image' && Boolean(histogram.value))

  function handlePointerMove(event: PointerEvent) {
    if (!analysisCanvas || !loupeCanvas || !scratchCanvas) {
      return
    }

    const currentSelection = selection.value
    const currentImageElement = imageElement.value

    if (currentSelection?.kind !== 'image' || !currentImageElement) {
      return
    }

    const bounds = currentImageElement.getBoundingClientRect()
    if (!bounds.width || !bounds.height) {
      return
    }

    const relativeX = clamp((event.clientX - bounds.left) / bounds.width, 0, 1)
    const relativeY = clamp((event.clientY - bounds.top) / bounds.height, 0, 1)

    const naturalX = Math.round(relativeX * Math.max(currentSelection.dimensions.width - 1, 0))
    const naturalY = Math.round(relativeY * Math.max(currentSelection.dimensions.height - 1, 0))

    const analysisX = Math.round(relativeX * Math.max(analysisCanvas.width - 1, 0))
    const analysisY = Math.round(relativeY * Math.max(analysisCanvas.height - 1, 0))

    const context = analysisCanvas.getContext('2d', {
      willReadFrequently: true,
    })

    if (!context) {
      return
    }

    const pixel = context.getImageData(analysisX, analysisY, 1, 1).data
    const color = {
      r: pixel[0] ?? 0,
      g: pixel[1] ?? 0,
      b: pixel[2] ?? 0,
      a: Number(((pixel[3] ?? 0) / 255).toFixed(2)),
    }

    activeSample.value = {
      x: naturalX,
      y: naturalY,
      color,
      hex: rgbaToHex(color),
      rgb: rgbaToRgbString(color),
      hsl: rgbaToHslString(color),
      alpha: `${Math.round(color.a * 100)}%`,
      loupeDataUrl: buildLoupeDataUrl(
        analysisCanvas,
        scratchCanvas,
        loupeCanvas,
        analysisX,
        analysisY,
      ),
    }
  }

  function handlePointerLeave() {
    activeSample.value = null
  }

  function storeActiveSwatch() {
    if (!activeSample.value) {
      return
    }

    swatches.value = [
      {
        id: swatchId += 1,
        sample: {
          ...activeSample.value,
          color: { ...activeSample.value.color },
        },
      },
      ...swatches.value,
    ].slice(0, 8)
  }

  function removeSwatch(id: number) {
    swatches.value = swatches.value.filter((swatch) => swatch.id !== id)
  }

  async function copyActiveSample(format: 'hex' | 'rgb' | 'hsl') {
    if (!activeSample.value || !navigator.clipboard) {
      return
    }

    const valueMap = {
      hex: activeSample.value.hex,
      rgb: activeSample.value.rgb,
      hsl: activeSample.value.hsl,
    }

    await navigator.clipboard.writeText(valueMap[format])
  }

  function toggleTransparencyGrid() {
    isTransparencyGridVisible.value = !isTransparencyGridVisible.value
  }

  return {
    activeSample,
    histogram,
    swatches,
    canUseTools,
    isTransparencyGridVisible,
    handlePointerMove,
    handlePointerLeave,
    storeActiveSwatch,
    removeSwatch,
    copyActiveSample,
    toggleTransparencyGrid,
  }
}

function createAnalysisSize(width: number, height: number) {
  const maxDimension = 2048
  const scale = Math.min(1, maxDimension / Math.max(width, height, 1))

  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

function buildLoupeDataUrl(
  sourceCanvas: HTMLCanvasElement,
  scratchCanvas: HTMLCanvasElement,
  loupeCanvas: HTMLCanvasElement,
  x: number,
  y: number,
): string {
  const sourceContext = sourceCanvas.getContext('2d', {
    willReadFrequently: true,
  })
  const scratchContext = scratchCanvas.getContext('2d')
  const loupeContext = loupeCanvas.getContext('2d')

  if (!sourceContext || !scratchContext || !loupeContext) {
    return ''
  }

  const radius = 5
  const cropSize = radius * 2 + 1

  scratchCanvas.width = cropSize
  scratchCanvas.height = cropSize

  const startX = clamp(x - radius, 0, Math.max(sourceCanvas.width - cropSize, 0))
  const startY = clamp(y - radius, 0, Math.max(sourceCanvas.height - cropSize, 0))

  const imageData = sourceContext.getImageData(startX, startY, cropSize, cropSize)
  scratchContext.putImageData(imageData, 0, 0)

  loupeCanvas.width = cropSize * 12
  loupeCanvas.height = cropSize * 12

  loupeContext.imageSmoothingEnabled = false
  loupeContext.clearRect(0, 0, loupeCanvas.width, loupeCanvas.height)
  loupeContext.drawImage(scratchCanvas, 0, 0, loupeCanvas.width, loupeCanvas.height)

  loupeContext.strokeStyle = 'rgba(16, 36, 38, 0.18)'
  for (let index = 0; index <= cropSize; index += 1) {
    const offset = index * 12
    loupeContext.beginPath()
    loupeContext.moveTo(offset, 0)
    loupeContext.lineTo(offset, loupeCanvas.height)
    loupeContext.stroke()
    loupeContext.beginPath()
    loupeContext.moveTo(0, offset)
    loupeContext.lineTo(loupeCanvas.width, offset)
    loupeContext.stroke()
  }

  loupeContext.strokeStyle = '#F38A55'
  loupeContext.lineWidth = 2
  loupeContext.strokeRect(radius * 12, radius * 12, 12, 12)

  return loupeCanvas.toDataURL('image/png')
}

function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()

    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Не удалось подготовить image tools canvas.'))
    image.src = source
  })
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}
