import type { RasterImageFrame } from './browser-raster'

interface ImageTracerModule {
  imagedataToSVG(imageData: ImageData, options?: Record<string, unknown>): string
}

let imageTracerPromise: Promise<ImageTracerModule> | null = null

export async function buildVectorizedSvgFromRaster(raster: RasterImageFrame): Promise<Blob> {
  const tracer = await loadImageTracer()
  const svgMarkup = tracer.imagedataToSVG(raster.imageData, {
    numberofcolors: raster.hasTransparency ? 24 : 16,
    colorquantcycles: 2,
    pathomit: 2,
    ltres: 0.5,
    qtres: 0.5,
    linefilter: true,
    strokewidth: 0,
    roundcoords: 2,
    viewbox: true,
    desc: false,
  })

  return new Blob([svgMarkup], {
    type: 'image/svg+xml',
  })
}

async function loadImageTracer(): Promise<ImageTracerModule> {
  imageTracerPromise ??=
    // @ts-expect-error imagetracerjs публикует JS entry без type declarations.
    import('imagetracerjs/imagetracer_v1.2.6.js').then((module) => module.default ?? module)
  return imageTracerPromise
}
