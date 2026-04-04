import { describe, expect, it } from 'vitest'
import {
  computeHistogram,
  rgbaToHex,
  rgbaToHslString,
  rgbaToRgbString,
} from '../viewer-color-tools'

describe('viewer color tools', () => {
  it('formats rgba values into common color notations', () => {
    const color = {
      r: 255,
      g: 128,
      b: 0,
      a: 0.5,
    }

    expect(rgbaToHex(color)).toBe('#FF8000')
    expect(rgbaToHex(color, true)).toBe('#FF800080')
    expect(rgbaToRgbString(color)).toBe('rgba(255, 128, 0, 0.5)')
    expect(rgbaToHslString(color)).toBe('hsla(30, 100%, 50%, 0.5)')
  })

  it('builds normalized channel histograms and skips fully transparent pixels', () => {
    const data = new Uint8ClampedArray([
      255,
      0,
      0,
      255,
      0,
      255,
      0,
      255,
      0,
      0,
      255,
      255,
      255,
      255,
      255,
      0,
    ])

    const histogram = computeHistogram(data, 4)

    expect(histogram.red).toEqual([1, 0, 0, 0.5])
    expect(histogram.green).toEqual([1, 0, 0, 0.5])
    expect(histogram.blue).toEqual([1, 0, 0, 0.5])
    expect(histogram.luminance).toEqual([1, 0, 0.5, 0])
  })
})
