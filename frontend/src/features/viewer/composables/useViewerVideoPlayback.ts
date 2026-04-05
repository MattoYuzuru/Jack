import { computed, ref, watch, type Ref } from 'vue'
import type { ViewerResolvedEntry } from '../application/viewer-runtime'
import { formatViewerVideoDuration } from '../application/viewer-video'

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

  watch(
    () => selection.value?.file.name,
    () => {
      isPlaying.value = false
      isMuted.value = false
      volume.value = 1
      playbackRate.value = 1
      currentTime.value = 0
      durationSeconds.value = selection.value?.kind === 'video' ? selection.value.layout.durationSeconds : 0
      isPictureInPictureActive.value = false
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

  const progressPercent = computed(() =>
    durationSeconds.value > 0 ? (currentTime.value / durationSeconds.value) * 100 : 0,
  )

  const currentTimeLabel = computed(() => formatViewerVideoDuration(currentTime.value))
  const durationLabel = computed(() => formatViewerVideoDuration(durationSeconds.value))
  const canUsePictureInPicture = computed(
    () =>
      typeof document !== 'undefined' &&
      'pictureInPictureEnabled' in document &&
      Boolean(document.pictureInPictureEnabled),
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
    element.muted = safeValue === 0 ? true : element.muted
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
    togglePlayback,
    seekTo,
    seekBy,
    setVolume,
    toggleMute,
    setPlaybackRate,
    togglePictureInPicture,
  }
}
