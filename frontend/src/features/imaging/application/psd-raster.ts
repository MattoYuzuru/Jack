import { canvasToRasterFrame, createRasterFrame, type RasterImageFrame } from './browser-raster'

interface PsdPixelData {
  data: Uint8Array | Uint8ClampedArray
  width: number
  height: number
}

interface PsdLikeDocument {
  canvas?: HTMLCanvasElement
  imageData?: PsdPixelData
}

interface PsdReadModule {
  readPsd(buffer: ArrayBuffer, options?: Record<string, unknown>): PsdLikeDocument
}

let psdModulePromise: Promise<PsdReadModule> | null = null

export interface PsdDecodeArtifact {
  raster: RasterImageFrame
  warnings: string[]
}

export async function decodePsdCompositeRaster(file: File): Promise<PsdDecodeArtifact> {
  const module = await loadPsdModule()
  const psd = module.readPsd(await file.arrayBuffer(), {
    useImageData: true,
    skipLayerImageData: true,
    skipThumbnail: true,
  })

  const raster = resolvePsdCompositeRaster(psd)

  return {
    raster,
    warnings: [
      'PSD сведен из composite image: editable-слои, направляющие и часть Photoshop-specific данных в target не переносятся.',
    ],
  }
}

async function loadPsdModule(): Promise<PsdReadModule> {
  psdModulePromise ??= import('ag-psd/dist-es/index.js').then((module) => {
    const candidate = (module.default ?? module) as PsdReadModule

    if (typeof candidate.readPsd !== 'function') {
      throw new Error('PSD adapter не смог загрузить readPsd API.')
    }

    return candidate
  })

  return psdModulePromise
}

function resolvePsdCompositeRaster(psd: PsdLikeDocument): RasterImageFrame {
  if (psd.imageData) {
    return createRasterFrame(
      new ImageData(
        new Uint8ClampedArray(psd.imageData.data),
        psd.imageData.width,
        psd.imageData.height,
      ),
    )
  }

  if (psd.canvas) {
    return canvasToRasterFrame(psd.canvas)
  }

  throw new Error('PSD adapter не нашёл composite image для browser-first decode.')
}
