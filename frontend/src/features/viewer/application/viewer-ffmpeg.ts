import { FFmpeg } from '@ffmpeg/ffmpeg'
import type { FileData } from '@ffmpeg/ffmpeg'
import ffmpegCoreUrl from '@ffmpeg/core?url'
import ffmpegWasmUrl from '@ffmpeg/core/wasm?url'

let ffmpegInstance: FFmpeg | null = null
let ffmpegLoadPromise: Promise<FFmpeg> | null = null

export async function loadViewerFfmpeg(): Promise<FFmpeg> {
  if (ffmpegInstance?.loaded) {
    return ffmpegInstance
  }

  if (!ffmpegLoadPromise) {
    ffmpegLoadPromise = (async () => {
      const ffmpeg = new FFmpeg()
      await ffmpeg.load({
        coreURL: ffmpegCoreUrl,
        wasmURL: ffmpegWasmUrl,
      })
      ffmpegInstance = ffmpeg
      return ffmpeg
    })().catch((error) => {
      ffmpegLoadPromise = null
      throw error
    })
  }

  return ffmpegLoadPromise
}

export async function safeDeleteViewerFfmpegFile(ffmpeg: FFmpeg, path: string) {
  try {
    await ffmpeg.deleteFile(path)
  } catch {
    // FFmpeg MEMFS может не содержать файл после неуспешного профиля, и это не
    // должно ломать cleanup-path поверх preview adapter'ов.
  }
}

export function normalizeViewerFfmpegBinary(data: FileData): ArrayBuffer {
  if (typeof data === 'string') {
    return new TextEncoder().encode(data).buffer
  }

  const copy = new Uint8Array(data.byteLength)
  copy.set(data)
  return copy.buffer
}

export function normalizeViewerFfmpegText(data: FileData): string {
  if (typeof data === 'string') {
    return data
  }

  return new TextDecoder().decode(data)
}

export function createViewerFfmpegSessionId(prefix: string): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `${prefix}-${crypto.randomUUID()}`
  }

  return `${prefix}-${Date.now()}`
}
