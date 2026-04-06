import { computed, nextTick, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { ViewerResolvedEntry } from '../application/viewer-runtime'
import { formatViewerVideoDuration } from '../application/viewer-video'
import {
  captureViewerVideoPoster,
  getViewerVideoFrameStepSeconds,
  prepareViewerSubtitleFile,
  type ViewerSubtitleFormat,
} from '../application/viewer-video-tools'

export const viewerVideoSubtitleAcceptAttribute = '.vtt,.srt,text/vtt,application/x-subrip'

export interface ViewerVideoSubtitleTrack {
  id: number
  label: string
  language: string
  cueCount: number
  objectUrl: string
  format: ViewerSubtitleFormat
  kind: 'subtitles'
}

export interface ViewerVideoPosterCapture {
  id: number
  objectUrl: string
  fileName: string
  timeSeconds: number
  timeLabel: string
  width: number
  height: number
}

export function useViewerVideoPlayback(
  selection: Ref<ViewerResolvedEntry | null>,
  videoElement: Ref<HTMLVideoElement | null>,
) {
  const isPlaying = ref(false)
  const isMuted = ref(false)
  const volume = ref(1)
  const playbackRate = ref(1)
  const currentTime = ref(0)
  const durationSeconds = ref(0)
  const isPictureInPictureActive = ref(false)
  const isLooping = ref(false)
  const assumedFrameRate = ref(24)
  const subtitleTracks = ref<ViewerVideoSubtitleTrack[]>([])
  const activeSubtitleTrackId = ref<'off' | number>('off')
  const subtitleMessage = ref('')
  const playbackMessage = ref('')
  const posterMessage = ref('')
  const posterCaptures = ref<ViewerVideoPosterCapture[]>([])

  let subtitleTrackId = 0
  let posterCaptureId = 0

  watch(
    () => selection.value?.file.name,
    () => {
      cleanupSubtitleTracks()
      cleanupPosterCaptures()

      isPlaying.value = false
      isMuted.value = false
      volume.value = 1
      playbackRate.value = 1
      currentTime.value = 0
      durationSeconds.value =
        selection.value?.kind === 'video' ? selection.value.layout.durationSeconds : 0
      isPictureInPictureActive.value = false
      isLooping.value = false
      assumedFrameRate.value = 24
      activeSubtitleTrackId.value = 'off'
      subtitleMessage.value = ''
      playbackMessage.value = ''
      posterMessage.value = ''
    },
    { immediate: true },
  )

  watch(
    videoElement,
    (element, previousElement, onCleanup) => {
      if (previousElement) {
        previousElement.pause()
      }

      if (!element) {
        return
      }

      const syncState = () => {
        isPlaying.value = !element.paused && !element.ended
        isMuted.value = element.muted
        volume.value = element.volume
        playbackRate.value = element.playbackRate
        currentTime.value = element.currentTime
        durationSeconds.value =
          Number.isFinite(element.duration) && element.duration > 0
            ? element.duration
            : selection.value?.kind === 'video'
              ? selection.value.layout.durationSeconds
              : 0
      }

      const syncPictureInPicture = () => {
        isPictureInPictureActive.value = document.pictureInPictureElement === element
      }

      element.volume = volume.value
      element.playbackRate = playbackRate.value
      element.loop = isLooping.value
      syncState()

      element.addEventListener('loadedmetadata', syncState)
      element.addEventListener('durationchange', syncState)
      element.addEventListener('timeupdate', syncState)
      element.addEventListener('play', syncState)
      element.addEventListener('pause', syncState)
      element.addEventListener('ended', syncState)
      element.addEventListener('volumechange', syncState)
      element.addEventListener('ratechange', syncState)
      element.addEventListener('enterpictureinpicture', syncPictureInPicture)
      element.addEventListener('leavepictureinpicture', syncPictureInPicture)

      onCleanup(() => {
        element.removeEventListener('loadedmetadata', syncState)
        element.removeEventListener('durationchange', syncState)
        element.removeEventListener('timeupdate', syncState)
        element.removeEventListener('play', syncState)
        element.removeEventListener('pause', syncState)
        element.removeEventListener('ended', syncState)
        element.removeEventListener('volumechange', syncState)
        element.removeEventListener('ratechange', syncState)
        element.removeEventListener('enterpictureinpicture', syncPictureInPicture)
        element.removeEventListener('leavepictureinpicture', syncPictureInPicture)
      })
    },
    { flush: 'post' },
  )

  watch(isLooping, (nextValue) => {
    const element = videoElement.value
    if (!element) {
      return
    }

    element.loop = nextValue
  })

  watch(
    [videoElement, subtitleTracks, activeSubtitleTrackId],
    async () => {
      await nextTick()
      syncSubtitleTrackModes()
    },
    { deep: true, flush: 'post' },
  )

  onBeforeUnmount(() => {
    cleanupSubtitleTracks()
    cleanupPosterCaptures()
  })

  const progressPercent = computed(() =>
    durationSeconds.value > 0 ? (currentTime.value / durationSeconds.value) * 100 : 0,
  )

  const currentTimeLabel = computed(() => formatViewerVideoDuration(currentTime.value))
  const durationLabel = computed(() => formatViewerVideoDuration(durationSeconds.value))
  const frameStepSeconds = computed(() => getViewerVideoFrameStepSeconds(assumedFrameRate.value))
  const frameStepLabel = computed(() => `${Math.round(frameStepSeconds.value * 1000)} ms`)
  const approximateFrameNumber = computed(() =>
    durationSeconds.value > 0 ? Math.floor(currentTime.value * assumedFrameRate.value) + 1 : 0,
  )
  const canUsePictureInPicture = computed(
    () =>
      typeof document !== 'undefined' &&
      'pictureInPictureEnabled' in document &&
      Boolean(document.pictureInPictureEnabled),
  )
  const activeSubtitleTrack = computed(
    () => subtitleTracks.value.find((track) => track.id === activeSubtitleTrackId.value) ?? null,
  )
  const subtitleCueCount = computed(() =>
    subtitleTracks.value.reduce((total, track) => total + track.cueCount, 0),
  )

  async function togglePlayback() {
    const element = videoElement.value
    if (!element) {
      return
    }

    if (element.paused || element.ended) {
      await element.play()
      return
    }

    element.pause()
  }

  function seekTo(seconds: number) {
    const element = videoElement.value
    if (!element) {
      return
    }

    const clampedValue = Math.min(Math.max(seconds, 0), durationSeconds.value || seconds)
    element.currentTime = clampedValue
    currentTime.value = clampedValue
  }

  function seekBy(deltaSeconds: number) {
    seekTo(currentTime.value + deltaSeconds)
  }

  function setVolume(nextValue: number) {
    const element = videoElement.value
    const safeValue = Math.min(Math.max(nextValue, 0), 1)

    volume.value = safeValue

    if (!element) {
      return
    }

    element.volume = safeValue
    if (safeValue > 0 && element.muted) {
      element.muted = false
    }

    if (safeValue === 0) {
      element.muted = true
    }
  }

  function toggleMute() {
    const element = videoElement.value
    if (!element) {
      return
    }

    element.muted = !element.muted
    isMuted.value = element.muted
  }

  function setPlaybackRate(nextRate: number) {
    const element = videoElement.value
    playbackRate.value = nextRate

    if (!element) {
      return
    }

    element.playbackRate = nextRate
  }

  function setAssumedFrameRate(nextRate: number) {
    assumedFrameRate.value = nextRate > 0 ? nextRate : 24
  }

  async function stepFrame(direction: -1 | 1) {
    const element = videoElement.value
    if (!element) {
      return
    }

    element.pause()
    seekTo(currentTime.value + frameStepSeconds.value * direction)
  }

  function toggleLoop() {
    isLooping.value = !isLooping.value
  }

  async function togglePictureInPicture() {
    const element = videoElement.value
    if (!element || !canUsePictureInPicture.value) {
      return
    }

    if (document.pictureInPictureElement === element) {
      await document.exitPictureInPicture()
      return
    }

    await element.requestPictureInPicture()
  }

  async function loadSubtitleFiles(source: FileList | File[]) {
    const incomingFiles = Array.from(source)
    if (!incomingFiles.length) {
      return
    }

    const nextTracks: ViewerVideoSubtitleTrack[] = []
    const rejectedFiles: string[] = []

    for (const file of incomingFiles) {
      try {
        const prepared = await prepareViewerSubtitleFile(file)
        nextTracks.push({
          id: (subtitleTrackId += 1),
          label: prepared.label,
          language: prepared.language,
          cueCount: prepared.cueCount,
          objectUrl: URL.createObjectURL(prepared.blob),
          format: prepared.format,
          kind: 'subtitles',
        })
      } catch (error) {
        rejectedFiles.push(error instanceof Error ? `${file.name}: ${error.message}` : file.name)
      }
    }

    if (nextTracks.length) {
      const mergedTracks = [...subtitleTracks.value, ...nextTracks]
      const overflowTracks = mergedTracks.slice(0, Math.max(mergedTracks.length - 8, 0))
      overflowTracks.forEach((track) => URL.revokeObjectURL(track.objectUrl))

      subtitleTracks.value = mergedTracks.slice(-8)
      activeSubtitleTrackId.value =
        nextTracks[nextTracks.length - 1]?.id ?? activeSubtitleTrackId.value
      subtitleMessage.value = `Subtitle tracks загружены: ${nextTracks.map((track) => track.label).join(', ')}.`
    } else if (rejectedFiles.length) {
      subtitleMessage.value = rejectedFiles.join(' ')
    }

    if (rejectedFiles.length && nextTracks.length) {
      subtitleMessage.value += ` ${rejectedFiles.join(' ')}`
    }
  }

  function setActiveSubtitleTrack(nextTrackId: 'off' | number) {
    activeSubtitleTrackId.value = nextTrackId
  }

  function removeSubtitleTrack(trackId: number) {
    const track = subtitleTracks.value.find((entry) => entry.id === trackId)
    if (!track) {
      return
    }

    URL.revokeObjectURL(track.objectUrl)
    subtitleTracks.value = subtitleTracks.value.filter((entry) => entry.id !== trackId)

    if (activeSubtitleTrackId.value === trackId) {
      activeSubtitleTrackId.value = subtitleTracks.value[0]?.id ?? 'off'
    }

    subtitleMessage.value = `Subtitle track \`${track.label}\` удалён из viewer session.`
  }

  function clearSubtitleTracks() {
    cleanupSubtitleTracks()
    activeSubtitleTrackId.value = 'off'
    subtitleMessage.value = 'Subtitle tracks очищены.'
  }

  async function capturePoster() {
    const currentSelection = selection.value
    const element = videoElement.value

    if (currentSelection?.kind !== 'video' || !element) {
      return
    }

    try {
      const snapshot = await captureViewerVideoPoster(element, currentSelection.file.name)
      const objectUrl = URL.createObjectURL(snapshot.blob)
      const nextCapture: ViewerVideoPosterCapture = {
        id: (posterCaptureId += 1),
        objectUrl,
        fileName: snapshot.fileName,
        timeSeconds: snapshot.timeSeconds,
        timeLabel: snapshot.timeLabel,
        width: snapshot.width,
        height: snapshot.height,
      }

      const nextCaptures = [nextCapture, ...posterCaptures.value]
      const overflowCaptures = nextCaptures.slice(8)
      overflowCaptures.forEach((capture) => URL.revokeObjectURL(capture.objectUrl))

      posterCaptures.value = nextCaptures.slice(0, 8)
      posterMessage.value = `Poster сохранён из точки ${snapshot.timeLabel}.`
    } catch (error) {
      posterMessage.value =
        error instanceof Error ? error.message : 'Не удалось сохранить poster из текущего кадра.'
    }
  }

  function removePosterCapture(captureId: number) {
    const capture = posterCaptures.value.find((entry) => entry.id === captureId)
    if (!capture) {
      return
    }

    URL.revokeObjectURL(capture.objectUrl)
    posterCaptures.value = posterCaptures.value.filter((entry) => entry.id !== captureId)
    posterMessage.value = `Poster ${capture.timeLabel} удалён из session gallery.`
  }

  function downloadPosterCapture(captureId: number) {
    const capture = posterCaptures.value.find((entry) => entry.id === captureId)
    if (!capture) {
      return
    }

    const anchor = document.createElement('a')
    anchor.href = capture.objectUrl
    anchor.download = capture.fileName
    anchor.click()
  }

  async function copyCurrentTimestamp() {
    if (!navigator.clipboard) {
      playbackMessage.value = 'Clipboard API недоступен в текущем окружении.'
      return
    }

    await navigator.clipboard.writeText(currentTimeLabel.value)
    playbackMessage.value = `Текущий timestamp ${currentTimeLabel.value} скопирован в clipboard.`
  }

  async function handleShortcutKeydown(event: KeyboardEvent) {
    if (selection.value?.kind !== 'video' || shouldIgnoreKeyboardEvent(event)) {
      return
    }

    switch (event.code) {
      case 'Space':
        event.preventDefault()
        await togglePlayback()
        return
      case 'ArrowLeft':
        event.preventDefault()
        if (event.shiftKey) {
          await stepFrame(-1)
          return
        }
        seekBy(-5)
        return
      case 'ArrowRight':
        event.preventDefault()
        if (event.shiftKey) {
          await stepFrame(1)
          return
        }
        seekBy(5)
        return
      case 'KeyM':
        event.preventDefault()
        toggleMute()
        return
      case 'KeyL':
        event.preventDefault()
        toggleLoop()
        return
      case 'KeyP':
        event.preventDefault()
        await togglePictureInPicture()
        return
      case 'KeyC':
        event.preventDefault()
        await copyCurrentTimestamp()
        return
      default:
        return
    }
  }

  function syncSubtitleTrackModes() {
    const element = videoElement.value
    if (!element) {
      return
    }

    // DOM textTracks не знают про наши id, поэтому синхронизируем режимы по текущему
    // порядку declarative <track>-узлов после каждого изменения session state.
    Array.from(element.textTracks).forEach((track, index) => {
      const descriptor = subtitleTracks.value[index]

      if (!descriptor) {
        track.mode = 'disabled'
        return
      }

      track.mode = descriptor.id === activeSubtitleTrackId.value ? 'showing' : 'disabled'
    })
  }

  function cleanupSubtitleTracks() {
    subtitleTracks.value.forEach((track) => URL.revokeObjectURL(track.objectUrl))
    subtitleTracks.value = []
  }

  function cleanupPosterCaptures() {
    posterCaptures.value.forEach((capture) => URL.revokeObjectURL(capture.objectUrl))
    posterCaptures.value = []
  }

  return {
    isPlaying,
    isMuted,
    volume,
    playbackRate,
    currentTime,
    durationSeconds,
    progressPercent,
    currentTimeLabel,
    durationLabel,
    canUsePictureInPicture,
    isPictureInPictureActive,
    isLooping,
    assumedFrameRate,
    frameStepSeconds,
    frameStepLabel,
    approximateFrameNumber,
    subtitleTracks,
    activeSubtitleTrack,
    activeSubtitleTrackId,
    subtitleCueCount,
    subtitleMessage,
    playbackMessage,
    posterMessage,
    posterCaptures,
    togglePlayback,
    seekTo,
    seekBy,
    setVolume,
    toggleMute,
    setPlaybackRate,
    setAssumedFrameRate,
    stepFrame,
    toggleLoop,
    togglePictureInPicture,
    loadSubtitleFiles,
    setActiveSubtitleTrack,
    removeSubtitleTrack,
    clearSubtitleTracks,
    capturePoster,
    removePosterCapture,
    downloadPosterCapture,
    copyCurrentTimestamp,
    handleShortcutKeydown,
  }
}

function shouldIgnoreKeyboardEvent(event: KeyboardEvent): boolean {
  const target = event.target

  if (!(target instanceof HTMLElement)) {
    return false
  }

  return (
    target.isContentEditable ||
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT' ||
    target.tagName === 'BUTTON'
  )
}
