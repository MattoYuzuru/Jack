import { computed, watch, type Ref } from 'vue'
import { ref } from 'vue'
import type { ViewerResolvedEntry } from '../application/viewer-runtime'
import { formatViewerAudioDuration } from '../application/viewer-audio-tools'

export function useViewerAudioPlayback(
  selection: Ref<ViewerResolvedEntry | null>,
  audioElement: Ref<HTMLAudioElement | null>,
) {
  const isPlaying = ref(false)
  const isMuted = ref(false)
  const volume = ref(1)
  const playbackRate = ref(1)
  const currentTime = ref(0)
  const durationSeconds = ref(0)
  const isLooping = ref(false)
  const playbackMessage = ref('')

  watch(
    () => selection.value?.file.name,
    () => {
      isPlaying.value = false
      isMuted.value = false
      volume.value = 1
      playbackRate.value = 1
      currentTime.value = 0
      durationSeconds.value =
        selection.value?.kind === 'audio' ? selection.value.layout.durationSeconds : 0
      isLooping.value = false
      playbackMessage.value = ''
    },
    { immediate: true },
  )

  watch(
    audioElement,
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
            : selection.value?.kind === 'audio'
              ? selection.value.layout.durationSeconds
              : 0
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

      onCleanup(() => {
        element.removeEventListener('loadedmetadata', syncState)
        element.removeEventListener('durationchange', syncState)
        element.removeEventListener('timeupdate', syncState)
        element.removeEventListener('play', syncState)
        element.removeEventListener('pause', syncState)
        element.removeEventListener('ended', syncState)
        element.removeEventListener('volumechange', syncState)
        element.removeEventListener('ratechange', syncState)
      })
    },
    { flush: 'post' },
  )

  watch(isLooping, (nextValue) => {
    const element = audioElement.value
    if (!element) {
      return
    }

    element.loop = nextValue
  })

  const progressPercent = computed(() =>
    durationSeconds.value > 0 ? (currentTime.value / durationSeconds.value) * 100 : 0,
  )
  const currentTimeLabel = computed(() => formatViewerAudioDuration(currentTime.value))
  const durationLabel = computed(() => formatViewerAudioDuration(durationSeconds.value))

  async function togglePlayback() {
    const element = audioElement.value
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
    const element = audioElement.value
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
    const element = audioElement.value
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
    const element = audioElement.value
    if (!element) {
      return
    }

    element.muted = !element.muted
    isMuted.value = element.muted
  }

  function setPlaybackRate(nextRate: number) {
    const element = audioElement.value
    playbackRate.value = nextRate

    if (!element) {
      return
    }

    element.playbackRate = nextRate
  }

  function toggleLoop() {
    isLooping.value = !isLooping.value
  }

  async function copyCurrentTimestamp() {
    if (!navigator.clipboard) {
      playbackMessage.value = 'Clipboard API недоступен в текущем окружении.'
      return
    }

    await navigator.clipboard.writeText(currentTimeLabel.value)
    playbackMessage.value = `Таймкод ${currentTimeLabel.value} скопирован в clipboard.`
  }

  function handleShortcutKeydown(event: KeyboardEvent) {
    if (selection.value?.kind !== 'audio') {
      return
    }

    const target = event.target
    if (
      target instanceof HTMLElement &&
      (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT')
    ) {
      return
    }

    if (event.code === 'Space') {
      event.preventDefault()
      void togglePlayback()
      return
    }

    if (event.code === 'ArrowLeft') {
      event.preventDefault()
      seekBy(-10)
      return
    }

    if (event.code === 'ArrowRight') {
      event.preventDefault()
      seekBy(10)
      return
    }

    if (event.key.toLowerCase() === 'm') {
      event.preventDefault()
      toggleMute()
      return
    }

    if (event.key.toLowerCase() === 'l') {
      event.preventDefault()
      toggleLoop()
      return
    }

    if (event.key.toLowerCase() === 'c') {
      event.preventDefault()
      void copyCurrentTimestamp()
    }
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
    isLooping,
    playbackMessage,
    togglePlayback,
    seekTo,
    seekBy,
    setVolume,
    toggleMute,
    setPlaybackRate,
    toggleLoop,
    copyCurrentTimestamp,
    handleShortcutKeydown,
  }
}
