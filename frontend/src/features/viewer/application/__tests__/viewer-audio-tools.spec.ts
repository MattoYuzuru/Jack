import { describe, expect, it } from 'vitest'
import {
  computeViewerAudioWaveform,
  estimateViewerAudioBitrateBitsPerSecond,
  formatViewerAudioBitrate,
  formatViewerAudioDuration,
  formatViewerChannelLayout,
  formatViewerSampleRate,
} from '../viewer-audio-tools'

describe('viewer audio tools', () => {
  it('formats duration, bitrate and technical labels', () => {
    expect(formatViewerAudioDuration(190)).toBe('03:10')
    expect(estimateViewerAudioBitrateBitsPerSecond(4_800_000, 200)).toBe(192_000)
    expect(formatViewerAudioBitrate(192_000)).toBe('192 kbps')
    expect(formatViewerAudioBitrate(null)).toBe('n/a')
    expect(formatViewerSampleRate(44_100)).toBe('44.1 kHz')
    expect(formatViewerChannelLayout(1)).toBe('Mono')
    expect(formatViewerChannelLayout(2)).toBe('Stereo')
  })

  it('normalizes waveform buckets from a decoded audio buffer', () => {
    const channels = [
      new Float32Array([0, 0.25, 0.5, 0.75, 1, 0.2, 0.4, 0.8]),
      new Float32Array([0, 0.1, 0.35, 0.6, 0.95, 0.3, 0.45, 0.7]),
    ]

    const audioBuffer = {
      length: 8,
      numberOfChannels: 2,
      getChannelData(channelIndex: number) {
        return channels[channelIndex] as Float32Array
      },
    } as AudioBuffer

    expect(computeViewerAudioWaveform(audioBuffer, 4)).toEqual([0.25, 0.75, 1, 0.8])
  })
})
