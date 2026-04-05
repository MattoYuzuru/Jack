import { describe, expect, it } from 'vitest'
import {
  convertViewerSrtToVtt,
  countViewerSubtitleCues,
  estimateViewerVideoBitrateBitsPerSecond,
  formatViewerAspectRatio,
  formatViewerVideoBitrate,
  getViewerVideoFrameStepSeconds,
  inferViewerSubtitleFormat,
  inferViewerSubtitleLanguage,
  prepareViewerSubtitleFile,
  resolveViewerVideoOrientation,
} from '../viewer-video-tools'

describe('viewer video tools', () => {
  it('formats common aspect ratios and orientation labels', () => {
    expect(formatViewerAspectRatio(1920, 1080)).toBe('16:9')
    expect(formatViewerAspectRatio(1080, 1920)).toBe('9:16')
    expect(resolveViewerVideoOrientation(1920, 1080)).toBe('Landscape')
    expect(resolveViewerVideoOrientation(1080, 1920)).toBe('Portrait')
  })

  it('estimates and formats bitrate values', () => {
    const bitrate = estimateViewerVideoBitrateBitsPerSecond(12_000_000, 24)

    expect(bitrate).toBe(4_000_000)
    expect(formatViewerVideoBitrate(bitrate)).toBe('4.00 Mbps')
    expect(formatViewerVideoBitrate(null)).toBe('n/a')
  })

  it('returns a deterministic frame step from assumed fps', () => {
    expect(getViewerVideoFrameStepSeconds(25)).toBe(0.04)
    expect(getViewerVideoFrameStepSeconds(Number.NaN)).toBeCloseTo(1 / 24, 4)
  })

  it('detects subtitle format and language from sidecar names', () => {
    expect(inferViewerSubtitleFormat('clip.en.vtt')).toBe('vtt')
    expect(inferViewerSubtitleFormat('clip.ru.srt')).toBe('srt')
    expect(inferViewerSubtitleFormat('clip.ass')).toBeNull()
    expect(inferViewerSubtitleLanguage('clip.en.vtt')).toBe('en')
    expect(inferViewerSubtitleLanguage('clip.subtitles.vtt')).toBe('und')
  })

  it('converts srt subtitles into browser-ready webvtt', () => {
    const source = `1
00:00:01,000 --> 00:00:02,500
Hello

2
00:00:03,000 --> 00:00:05,000
World`

    expect(convertViewerSrtToVtt(source)).toContain('WEBVTT')
    expect(convertViewerSrtToVtt(source)).toContain('00:00:01.000 --> 00:00:02.500')
    expect(countViewerSubtitleCues(source, 'srt')).toBe(2)
  })

  it('prepares sidecar files as normalized vtt blobs', async () => {
    const result = await prepareViewerSubtitleFile(
      new File(
        [
          `1
00:00:00,000 --> 00:00:01,000
Jack`,
        ],
        'movie.en.srt',
        { type: 'application/x-subrip' },
      ),
    )

    expect(result.format).toBe('srt')
    expect(result.label).toBe('movie.en')
    expect(result.language).toBe('en')
    expect(result.cueCount).toBe(1)
    expect(await result.blob.text()).toContain('WEBVTT')
  })
})
