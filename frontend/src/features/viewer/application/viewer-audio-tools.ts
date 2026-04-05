export function formatViewerAudioDuration(durationSeconds: number): string {
  if (!Number.isFinite(durationSeconds) || durationSeconds < 0) {
    return '00:00'
  }

  const roundedSeconds = Math.floor(durationSeconds)
  const hours = Math.floor(roundedSeconds / 3600)
  const minutes = Math.floor((roundedSeconds % 3600) / 60)
  const seconds = roundedSeconds % 60

  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function estimateViewerAudioBitrateBitsPerSecond(
  fileSizeBytes: number,
  durationSeconds: number,
): number | null {
  if (fileSizeBytes <= 0 || durationSeconds <= 0 || !Number.isFinite(durationSeconds)) {
    return null
  }

  return Math.round((fileSizeBytes * 8) / durationSeconds)
}

export function formatViewerAudioBitrate(bitsPerSecond: number | null): string {
  if (!bitsPerSecond || bitsPerSecond <= 0 || !Number.isFinite(bitsPerSecond)) {
    return 'n/a'
  }

  if (bitsPerSecond >= 1_000_000) {
    return `${(bitsPerSecond / 1_000_000).toFixed(2)} Mbps`
  }

  return `${Math.round(bitsPerSecond / 1_000)} kbps`
}

export function formatViewerSampleRate(sampleRate: number | null): string {
  if (!sampleRate || sampleRate <= 0) {
    return 'n/a'
  }

  return `${(sampleRate / 1000).toFixed(sampleRate >= 96_000 ? 0 : 1)} kHz`
}

export function formatViewerChannelLayout(channelCount: number | null): string {
  if (!channelCount || channelCount <= 0) {
    return 'n/a'
  }

  if (channelCount === 1) {
    return 'Mono'
  }

  if (channelCount === 2) {
    return 'Stereo'
  }

  return `${channelCount} ch`
}

export function computeViewerAudioWaveform(audioBuffer: AudioBuffer, bucketCount = 56): number[] {
  if (!audioBuffer.length || !audioBuffer.numberOfChannels) {
    return []
  }

  const samplesPerBucket = Math.max(1, Math.floor(audioBuffer.length / bucketCount))
  const buckets: number[] = []

  for (let bucketIndex = 0; bucketIndex < bucketCount; bucketIndex += 1) {
    const start = bucketIndex * samplesPerBucket
    const end = Math.min(audioBuffer.length, start + samplesPerBucket)
    let peak = 0

    for (let channelIndex = 0; channelIndex < audioBuffer.numberOfChannels; channelIndex += 1) {
      const channel = audioBuffer.getChannelData(channelIndex)

      for (let sampleIndex = start; sampleIndex < end; sampleIndex += 1) {
        const amplitude = Math.abs(channel[sampleIndex] ?? 0)
        if (amplitude > peak) {
          peak = amplitude
        }
      }
    }

    buckets.push(Number(peak.toFixed(4)))
  }

  return normalizeViewerAudioWaveform(buckets)
}

function normalizeViewerAudioWaveform(values: number[]): number[] {
  const peak = Math.max(...values, 1)
  return values.map((value) => Number((value / peak).toFixed(4)))
}
