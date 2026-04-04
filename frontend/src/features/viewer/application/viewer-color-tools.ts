export interface ViewerColorValue {
  r: number
  g: number
  b: number
  a: number
}

export interface ViewerHistogram {
  red: number[]
  green: number[]
  blue: number[]
  luminance: number[]
}

export function rgbaToHex(color: ViewerColorValue, includeAlpha = false): string {
  const channels = [color.r, color.g, color.b]

  if (includeAlpha) {
    channels.push(Math.round(color.a * 255))
  }

  return `#${channels.map((channel) => channel.toString(16).padStart(2, '0')).join('').toUpperCase()}`
}

export function rgbaToRgbString(color: ViewerColorValue): string {
  const alpha = Number(color.a.toFixed(2))
  return `rgba(${color.r}, ${color.g}, ${color.b}, ${alpha})`
}

export function rgbaToHslString(color: ViewerColorValue): string {
  const red = color.r / 255
  const green = color.g / 255
  const blue = color.b / 255

  const max = Math.max(red, green, blue)
  const min = Math.min(red, green, blue)
  const delta = max - min

  const lightness = (max + min) / 2
  const saturation =
    delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1))

  let hue = 0

  if (delta !== 0) {
    switch (max) {
      case red:
        hue = 60 * (((green - blue) / delta) % 6)
        break
      case green:
        hue = 60 * ((blue - red) / delta + 2)
        break
      default:
        hue = 60 * ((red - green) / delta + 4)
        break
    }
  }

  if (hue < 0) {
    hue += 360
  }

  return `hsla(${Math.round(hue)}, ${Math.round(saturation * 100)}%, ${Math.round(lightness * 100)}%, ${Number(color.a.toFixed(2))})`
}

export function computeHistogram(data: Uint8ClampedArray, bucketCount = 24): ViewerHistogram {
  const histogram: ViewerHistogram = {
    red: createBuckets(bucketCount),
    green: createBuckets(bucketCount),
    blue: createBuckets(bucketCount),
    luminance: createBuckets(bucketCount),
  }

  const bucketSize = 256 / bucketCount

  for (let index = 0; index < data.length; index += 4) {
    const alpha = data[index + 3] ?? 0
    if (alpha === 0) {
      continue
    }

    const red = data[index] ?? 0
    const green = data[index + 1] ?? 0
    const blue = data[index + 2] ?? 0
    const luminance = Math.round(0.2126 * red + 0.7152 * green + 0.0722 * blue)

    const redIndex = Math.min(bucketCount - 1, Math.floor(red / bucketSize))
    const greenIndex = Math.min(bucketCount - 1, Math.floor(green / bucketSize))
    const blueIndex = Math.min(bucketCount - 1, Math.floor(blue / bucketSize))
    const luminanceIndex = Math.min(bucketCount - 1, Math.floor(luminance / bucketSize))

    histogram.red[redIndex] = (histogram.red[redIndex] ?? 0) + 1
    histogram.green[greenIndex] = (histogram.green[greenIndex] ?? 0) + 1
    histogram.blue[blueIndex] = (histogram.blue[blueIndex] ?? 0) + 1
    histogram.luminance[luminanceIndex] = (histogram.luminance[luminanceIndex] ?? 0) + 1
  }

  return normalizeHistogram(histogram)
}

function createBuckets(bucketCount: number): number[] {
  return Array.from({ length: bucketCount }, () => 0)
}

function normalizeHistogram(histogram: ViewerHistogram): ViewerHistogram {
  const normalizedEntries = Object.entries(histogram).map(([channel, values]) => {
    const max = Math.max(...values, 1)
    return [channel, values.map((value: number) => Number((value / max).toFixed(4)))] as const
  })

  return Object.fromEntries(normalizedEntries) as ViewerHistogram
}
