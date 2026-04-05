<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useViewerWorkspace } from '../features/viewer/composables/useViewerWorkspace'
import { useViewerImageTools } from '../features/viewer/composables/useViewerImageTools'
import {
  useViewerVideoPlayback,
  viewerVideoSubtitleAcceptAttribute,
} from '../features/viewer/composables/useViewerVideoPlayback'
import { useViewerAudioPlayback } from '../features/viewer/composables/useViewerAudioPlayback'
import { findViewerDocumentMatches } from '../features/viewer/application/viewer-document'
import { formatViewerVideoDuration } from '../features/viewer/application/viewer-video'
import { formatViewerVideoBitrate } from '../features/viewer/application/viewer-video-tools'
import {
  formatViewerAudioBitrate,
  formatViewerAudioDuration,
  formatViewerChannelLayout,
  formatViewerSampleRate,
} from '../features/viewer/application/viewer-audio-tools'
import {
  createEmptyEditableMetadata,
  type ViewerEditableMetadata,
} from '../features/viewer/application/viewer-metadata'
import {
  canEmbedMetadata,
  exportViewerMetadata,
} from '../features/viewer/application/viewer-metadata-writer'

const fileInput = ref<HTMLInputElement | null>(null)
const subtitleInput = ref<HTMLInputElement | null>(null)
const previewStage = ref<HTMLElement | null>(null)
const previewImage = ref<HTMLImageElement | null>(null)
const previewVideo = ref<HTMLVideoElement | null>(null)
const previewAudio = ref<HTMLAudioElement | null>(null)
const isDragActive = ref(false)
const isFullscreen = ref(false)
const metadataQuery = ref('')
const audioMetadataQuery = ref('')
const documentQuery = ref('')
const documentSheetIndex = ref(0)
const documentSlideIndex = ref(0)
const documentDatabaseTableIndex = ref(0)
const activeDocumentMatchIndex = ref(0)
const metadataDraft = ref<ViewerEditableMetadata>(createEmptyEditableMetadata())
const isSavingMetadata = ref(false)
const metadataSaveMessage = ref('')
const documentActionMessage = ref('')

const videoPlaybackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const audioPlaybackRates = [0.75, 1, 1.25, 1.5, 2]
const videoFrameRateOptions = [23.976, 24, 25, 29.97, 30, 50, 59.94, 60]
const videoShortcutHints = [
  { keys: 'Space', description: 'Play / pause' },
  { keys: '← / →', description: 'Seek -5s / +5s' },
  { keys: 'Shift + ← / →', description: 'Step one frame назад / вперёд' },
  { keys: 'M', description: 'Mute / unmute' },
  { keys: 'L', description: 'Toggle loop' },
  { keys: 'P', description: 'Picture-in-picture' },
  { keys: 'C', description: 'Copy current timestamp' },
]
const audioShortcutHints = [
  { keys: 'Space', description: 'Play / pause' },
  { keys: '← / →', description: 'Seek -10s / +10s' },
  { keys: 'M', description: 'Mute / unmute' },
  { keys: 'L', description: 'Toggle loop' },
  { keys: 'C', description: 'Copy current timestamp' },
]

const browserNativeFormats = computed(() =>
  imageFormats.value.filter((definition) => definition.previewPipeline === 'browser-native'),
)

const serverImageFormats = computed(() =>
  imageFormats.value.filter((definition) => definition.previewPipeline === 'server-assisted'),
)

const activeDocumentFormats = computed(() =>
  documentFormats.value.filter((definition) => definition.previewPipeline !== 'planned'),
)

const plannedDocumentFormats = computed(() =>
  documentFormats.value.filter((definition) => definition.previewPipeline === 'planned'),
)

const activeMediaFormats = computed(() =>
  mediaFormats.value.filter((definition) => definition.previewPipeline !== 'planned'),
)

const plannedMediaFormats = computed(() =>
  mediaFormats.value.filter((definition) => definition.previewPipeline === 'planned'),
)

const activeAudioFormats = computed(() =>
  audioFormats.value.filter((definition) => definition.previewPipeline !== 'planned'),
)

const plannedAudioFormats = computed(() =>
  audioFormats.value.filter((definition) => definition.previewPipeline === 'planned'),
)

const {
  selection,
  isLoading,
  errorMessage,
  loadingMessage,
  viewerAcceptAttribute,
  imageFormats,
  documentFormats,
  mediaFormats,
  audioFormats,
  zoom,
  rotation,
  viewportTransform,
  selectFile,
  clearSelection,
  zoomIn,
  zoomOut,
  rotateLeft,
  rotateRight,
  resetViewportTransform,
} = useViewerWorkspace()

const {
  activeSample,
  histogram,
  swatches,
  canUseTools,
  isTransparencyGridVisible,
  handlePointerMove,
  handlePointerLeave,
  storeActiveSwatch,
  removeSwatch,
  copyActiveSample,
  toggleTransparencyGrid,
} = useViewerImageTools(selection, previewImage)

const {
  isPlaying: isVideoPlaying,
  isMuted: isVideoMuted,
  volume: videoVolume,
  playbackRate: videoPlaybackRate,
  currentTime: videoCurrentTime,
  durationSeconds: videoDurationSeconds,
  progressPercent: videoProgressPercent,
  currentTimeLabel: videoCurrentTimeLabel,
  durationLabel: videoDurationLabel,
  canUsePictureInPicture,
  isPictureInPictureActive,
  isLooping,
  assumedFrameRate,
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
} = useViewerVideoPlayback(selection, previewVideo)

const {
  isPlaying: isAudioPlaying,
  isMuted: isAudioMuted,
  volume: audioVolume,
  playbackRate: audioPlaybackRate,
  currentTime: audioCurrentTime,
  durationSeconds: audioDurationSeconds,
  progressPercent: audioProgressPercent,
  currentTimeLabel: audioCurrentTimeLabel,
  durationLabel: audioDurationLabel,
  isLooping: isAudioLooping,
  playbackMessage: audioPlaybackMessage,
  togglePlayback: toggleAudioPlayback,
  seekTo: seekAudioTo,
  seekBy: seekAudioBy,
  setVolume: setAudioVolume,
  toggleMute: toggleAudioMute,
  setPlaybackRate: setAudioPlaybackRate,
  toggleLoop: toggleAudioLoop,
  copyCurrentTimestamp: copyAudioTimestamp,
  handleShortcutKeydown: handleAudioShortcutKeydown,
} = useViewerAudioPlayback(selection, previewAudio)

watch(
  () => (selection.value?.kind === 'image' ? selection.value.metadata.editable : null),
  (editableMetadata) => {
    metadataDraft.value = editableMetadata ? { ...editableMetadata } : createEmptyEditableMetadata()
    metadataSaveMessage.value = ''
    metadataQuery.value = ''
  },
  { immediate: true },
)

watch(
  () => selection.value?.file.name,
  () => {
    audioMetadataQuery.value = ''
  },
)

watch(
  () => selection.value?.file.name,
  () => {
    documentQuery.value = ''
    documentSheetIndex.value = 0
    documentSlideIndex.value = 0
    documentDatabaseTableIndex.value = 0
    activeDocumentMatchIndex.value = 0
    documentActionMessage.value = ''
  },
)

const selectionFacts = computed(() => {
  if (!selection.value) {
    return []
  }

  const items = [
    { label: 'Имя файла', value: selection.value.file.name },
    {
      label: 'Размер',
      value: new Intl.NumberFormat('ru-RU').format(selection.value.file.size) + ' bytes',
    },
    { label: 'Расширение', value: selection.value.extension || 'unknown' },
    { label: 'MIME', value: selection.value.file.type || 'Не определён' },
  ]

  if (selection.value.kind === 'image') {
    items.push({
      label: 'Размерность',
      value: `${selection.value.dimensions.width} x ${selection.value.dimensions.height}`,
    })
    items.push({
      label: 'Preview path',
      value: selection.value.previewLabel,
    })
    items.push(...selection.value.metadata.summary)
  }

  if (selection.value.kind === 'document') {
    items.push({
      label: 'Preview path',
      value: selection.value.previewLabel,
    })
    items.push(...selection.value.summary)
  }

  if (selection.value.kind === 'video') {
    items.push({
      label: 'Preview path',
      value: selection.value.previewLabel,
    })
    items.push(...selection.value.summary)
  }

  if (selection.value.kind === 'audio') {
    items.push({
      label: 'Preview path',
      value: selection.value.previewLabel,
    })
    items.push(...selection.value.summary)
  }

  return items
})

const filteredMetadataGroups = computed(() => {
  if (selection.value?.kind !== 'image') {
    return []
  }

  const normalizedQuery = metadataQuery.value.trim().toLowerCase()
  const groups = selection.value.metadata.groups

  if (!normalizedQuery) {
    return groups
  }

  return groups
    .map((group) => ({
      ...group,
      entries: group.entries.filter(
        (entry) =>
          entry.label.toLowerCase().includes(normalizedQuery) ||
          entry.value.toLowerCase().includes(normalizedQuery),
      ),
    }))
    .filter((group) => group.entries.length > 0)
})

const metadataThumbnail = computed(() =>
  selection.value?.kind === 'image' ? selection.value.metadata.thumbnailDataUrl : null,
)

const metadataEmbeddingAvailable = computed(() =>
  selection.value?.kind === 'image' ? canEmbedMetadata(selection.value.file.name) : false,
)

const documentMatches = computed(() => {
  if (selection.value?.kind !== 'document') {
    return []
  }

  return findViewerDocumentMatches(selection.value.searchableText, documentQuery.value)
})

watch(documentMatches, (matches) => {
  activeDocumentMatchIndex.value = matches.length
    ? Math.min(activeDocumentMatchIndex.value, matches.length - 1)
    : 0
})

const documentWarnings = computed(() =>
  selection.value?.kind === 'document' ? selection.value.warnings : [],
)

const videoWarnings = computed(() =>
  selection.value?.kind === 'video' ? selection.value.warnings : [],
)

const audioWarnings = computed(() =>
  selection.value?.kind === 'audio' ? selection.value.warnings : [],
)

const videoStageMetrics = computed(() => {
  if (selection.value?.kind !== 'video') {
    return []
  }

  return [
    `Duration: ${formatViewerVideoDuration(selection.value.layout.durationSeconds)}`,
    `Frame: ${selection.value.layout.width} x ${selection.value.layout.height}`,
    `Aspect: ${selection.value.layout.metadata.aspectRatio}`,
    subtitleTracks.value.length ? `Subs: ${subtitleTracks.value.length}` : 'Subs: off',
  ]
})

const videoMetadataCards = computed(() => {
  if (selection.value?.kind !== 'video') {
    return []
  }

  return [
    {
      label: 'Aspect Ratio',
      value: selection.value.layout.metadata.aspectRatio,
    },
    {
      label: 'Orientation',
      value: selection.value.layout.metadata.orientation,
    },
    {
      label: 'Estimated Bitrate',
      value: formatViewerVideoBitrate(
        selection.value.layout.metadata.estimatedBitrateBitsPerSecond,
      ),
    },
    {
      label: 'Approx Frame',
      value: approximateFrameNumber.value ? `#${approximateFrameNumber.value}` : 'n/a',
    },
    {
      label: 'Subtitles',
      value: subtitleTracks.value.length
        ? `${subtitleTracks.value.length} tracks / ${subtitleCueCount.value} cues`
        : 'No sidecar loaded',
    },
    {
      label: 'Poster Gallery',
      value: posterCaptures.value.length ? `${posterCaptures.value.length} captures` : 'Empty',
    },
  ]
})

const filteredAudioMetadataGroups = computed(() => {
  if (selection.value?.kind !== 'audio') {
    return []
  }

  const normalizedQuery = audioMetadataQuery.value.trim().toLowerCase()
  const groups = selection.value.metadataGroups

  if (!normalizedQuery) {
    return groups
  }

  return groups
    .map((group) => ({
      ...group,
      entries: group.entries.filter(
        (entry) =>
          entry.label.toLowerCase().includes(normalizedQuery) ||
          entry.value.toLowerCase().includes(normalizedQuery),
      ),
    }))
    .filter((group) => group.entries.length > 0)
})

const audioStageMetrics = computed(() => {
  if (selection.value?.kind !== 'audio') {
    return []
  }

  return [
    `Duration: ${formatViewerAudioDuration(selection.value.layout.durationSeconds)}`,
    `Rate: ${formatViewerSampleRate(selection.value.layout.metadata.sampleRate)}`,
    `Channels: ${formatViewerChannelLayout(selection.value.layout.metadata.channelCount)}`,
    selection.value.artworkDataUrl ? 'Artwork: embedded' : 'Artwork: none',
  ]
})

const audioMetadataCards = computed(() => {
  if (selection.value?.kind !== 'audio') {
    return []
  }

  return [
    {
      label: 'Estimated Bitrate',
      value: formatViewerAudioBitrate(
        selection.value.layout.metadata.estimatedBitrateBitsPerSecond,
      ),
    },
    {
      label: 'Sample Rate',
      value: formatViewerSampleRate(selection.value.layout.metadata.sampleRate),
    },
    {
      label: 'Channels',
      value: formatViewerChannelLayout(selection.value.layout.metadata.channelCount),
    },
    {
      label: 'Codec',
      value: selection.value.layout.metadata.codec ?? 'n/a',
    },
    {
      label: 'Container',
      value: selection.value.layout.metadata.container ?? 'n/a',
    },
    {
      label: 'Waveform',
      value: selection.value.layout.waveform.length
        ? `${selection.value.layout.waveform.length} buckets`
        : 'Unavailable',
    },
  ]
})

const documentOutline = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'html') {
    return []
  }

  return selection.value.layout.outline
})

const documentTable = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'table') {
    return null
  }

  return selection.value.layout.table
})

const documentWorkbook = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'workbook') {
    return null
  }

  return selection.value.layout
})

const activeDocumentSheet = computed(() => {
  if (!documentWorkbook.value) {
    return null
  }

  const maxIndex = Math.max(documentWorkbook.value.sheets.length - 1, 0)
  const safeIndex = Math.min(documentSheetIndex.value, maxIndex)

  return documentWorkbook.value.sheets[safeIndex] ?? null
})

const documentParagraphs = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'text') {
    return []
  }

  return selection.value.layout.paragraphs
})

const documentSlides = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'slides') {
    return []
  }

  return selection.value.layout.slides
})

const activeDocumentSlide = computed(() => documentSlides.value[documentSlideIndex.value] ?? null)

const documentDatabase = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'database') {
    return null
  }

  return selection.value.layout
})

const activeDocumentDatabaseTable = computed(() => {
  if (!documentDatabase.value) {
    return null
  }

  const maxIndex = Math.max(documentDatabase.value.tables.length - 1, 0)
  const safeIndex = Math.min(documentDatabaseTableIndex.value, maxIndex)

  return documentDatabase.value.tables[safeIndex] ?? null
})

const documentModeLabel = computed(() => {
  if (selection.value?.kind !== 'document') {
    return ''
  }

  const labelMap: Record<string, string> = {
    pdf: 'PDF Embed',
    text: 'Text Reader',
    table: 'Table Preview',
    html: 'HTML Canvas',
    workbook: 'Workbook Preview',
    slides: 'Slide Deck',
    database: 'Database Preview',
  }

  return labelMap[selection.value.layout.mode] ?? 'Document Preview'
})

const documentStageMetrics = computed(() => {
  if (selection.value?.kind !== 'document') {
    return []
  }

  return selection.value.summary.slice(0, 4).map((item) => `${item.label}: ${item.value}`)
})

const documentSearchPlaceholder = computed(() => {
  if (selection.value?.kind !== 'document') {
    return 'Search document...'
  }

  const placeholderMap: Record<string, string> = {
    pdf: 'invoice, total, section...',
    text: 'paragraph, keyword, note...',
    table: 'column, customer, value...',
    html: 'heading, link text, paragraph...',
    workbook: 'sheet name, cell value, total...',
    slides: 'slide title, bullet, agenda...',
    database: 'table, column, schema, value...',
  }

  return placeholderMap[selection.value.layout.mode] ?? 'Search document...'
})

const activeDocumentMatch = computed(
  () => documentMatches.value[activeDocumentMatchIndex.value] ?? null,
)

function openFilePicker() {
  fileInput.value?.click()
}

function openSubtitlePicker() {
  subtitleInput.value?.click()
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) {
    return
  }

  void selectFile(file)
  target.value = ''
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  isDragActive.value = true
}

function onDragLeave(event: DragEvent) {
  if (event.currentTarget === event.target) {
    isDragActive.value = false
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  isDragActive.value = false

  const file = event.dataTransfer?.files?.[0]
  if (!file) {
    return
  }

  void selectFile(file)
}

async function toggleFullscreen() {
  if (!previewStage.value) {
    return
  }

  if (document.fullscreenElement) {
    await document.exitFullscreen()
    return
  }

  await previewStage.value.requestFullscreen?.()
}

function syncFullscreenState() {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

async function saveMetadataDraft() {
  if (selection.value?.kind !== 'image') {
    return
  }

  isSavingMetadata.value = true
  metadataSaveMessage.value = ''

  try {
    const result = await exportViewerMetadata(selection.value.file, metadataDraft.value)
    downloadBlob(result.blob, result.fileName)
    metadataSaveMessage.value =
      result.mode === 'embedded-jpeg'
        ? 'Новый JPEG с обновлёнными EXIF-полями собран и скачан.'
        : 'Metadata sidecar JSON собран и скачан.'
  } catch (error) {
    metadataSaveMessage.value =
      error instanceof Error ? error.message : 'Не удалось собрать metadata export.'
  } finally {
    isSavingMetadata.value = false
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')

  anchor.href = objectUrl
  anchor.download = fileName
  anchor.click()

  URL.revokeObjectURL(objectUrl)
}

async function copyDocumentText() {
  if (selection.value?.kind !== 'document') {
    return
  }

  if (!navigator.clipboard) {
    documentActionMessage.value = 'Clipboard API недоступен в текущем окружении.'
    return
  }

  await navigator.clipboard.writeText(selection.value.searchableText)
  documentActionMessage.value = 'Извлечённый text layer скопирован в clipboard.'
}

function downloadDocumentText() {
  if (selection.value?.kind !== 'document') {
    return
  }

  const extensionIndex = selection.value.file.name.lastIndexOf('.')
  const baseName =
    extensionIndex > 0
      ? selection.value.file.name.slice(0, extensionIndex)
      : selection.value.file.name

  downloadBlob(
    new Blob([selection.value.searchableText], { type: 'text/plain;charset=utf-8' }),
    `${baseName}.jack-extracted.txt`,
  )
  documentActionMessage.value = 'Извлечённый text layer собран в отдельный `.txt` файл.'
}

function clearDocumentSearch() {
  documentQuery.value = ''
  activeDocumentMatchIndex.value = 0
}

function focusDocumentMatch(index: number) {
  activeDocumentMatchIndex.value = index
}

function selectDocumentSheet(index: number) {
  documentSheetIndex.value = index
}

function selectDocumentSlide(index: number) {
  documentSlideIndex.value = index
}

function selectDocumentDatabaseTable(index: number) {
  documentDatabaseTableIndex.value = index
}

function onVideoSeekInput(event: Event) {
  const target = event.target as HTMLInputElement
  seekTo(Number(target.value))
}

function onVideoVolumeInput(event: Event) {
  const target = event.target as HTMLInputElement
  setVolume(Number(target.value))
}

function onVideoRateChange(event: Event) {
  const target = event.target as HTMLSelectElement
  setPlaybackRate(Number(target.value))
}

function onVideoFrameRateChange(event: Event) {
  const target = event.target as HTMLSelectElement
  setAssumedFrameRate(Number(target.value))
}

function onAudioSeekInput(event: Event) {
  const target = event.target as HTMLInputElement
  seekAudioTo(Number(target.value))
}

function onAudioVolumeInput(event: Event) {
  const target = event.target as HTMLInputElement
  setAudioVolume(Number(target.value))
}

function onAudioRateChange(event: Event) {
  const target = event.target as HTMLSelectElement
  setAudioPlaybackRate(Number(target.value))
}

async function onSubtitleChange(event: Event) {
  const target = event.target as HTMLInputElement
  const files = target.files

  if (!files?.length) {
    return
  }

  await loadSubtitleFiles(files)
  target.value = ''
}

function handleWorkspaceKeydown(event: KeyboardEvent) {
  if (selection.value?.kind === 'video') {
    handleShortcutKeydown(event)
    return
  }

  if (selection.value?.kind === 'audio') {
    handleAudioShortcutKeydown(event)
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
  window.addEventListener('keydown', handleWorkspaceKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreenState)
  window.removeEventListener('keydown', handleWorkspaceKeydown)
})
</script>

<template>
  <main class="workspace-shell viewer-workspace">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack of all trades</p>
          <p class="brand-lockup__title">Viewer Workspace</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <RouterLink class="back-link" to="/">Back to Home</RouterLink>
        <span class="chip-pill">Images + Docs + Media</span>
        <span class="chip-pill chip-pill--accent">Audio Workbench</span>
      </div>
    </header>

    <section class="viewer-hero-grid">
      <article class="panel-surface viewer-intro">
        <p class="eyebrow">Iteration 03 · File Viewer</p>
        <h1>
          Viewer теперь закрывает и audio slice: waveform, tag metadata и точный playback внутри
          того же workspace.
        </h1>
        <p class="lead">
          Архитектура остаётся registry-driven: image, document, video и audio семьи живут в одном
          маршруте, но каждая получает свой tooling-layer поверх общего stage/runtime. Для audio это
          означает waveform, artwork, tag inspector и compatibility bridge для legacy контейнеров
          без разрастания workspace в набор format-specific веток.
        </p>

        <div
          class="viewer-dropzone"
          :class="{ 'viewer-dropzone--active': isDragActive }"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <input
            ref="fileInput"
            class="visually-hidden"
            type="file"
            :accept="viewerAcceptAttribute"
            @change="onFileChange"
          />
          <input
            ref="subtitleInput"
            class="visually-hidden"
            type="file"
            :accept="viewerVideoSubtitleAcceptAttribute"
            multiple
            @change="onSubtitleChange"
          />

          <div class="viewer-dropzone__copy">
            <strong>Загрузить файл в viewer</strong>
            <span>
              Viewer уже держит image и document roadmap, video workbench и теперь закрывает весь
              audio slice: `mp3`, `wav`, `aac`, `flac`, `ogg`, `opus`, `aiff` сходятся в один
              workspace через native path или server-assisted audio preview, а поверх этого работают
              waveform, artwork, keyboard flow и richer tag metadata inspector.
            </span>
          </div>

          <div class="viewer-dropzone__actions">
            <button
              class="action-button action-button--accent"
              type="button"
              @click="openFilePicker"
            >
              Pick File
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!selection"
              @click="clearSelection"
            >
              Clear
            </button>
          </div>
        </div>

        <div class="signal-row">
          <span class="chip-pill">Image tooling live</span>
          <span class="chip-pill">Document stack complete</span>
          <span class="chip-pill">Video precision controls</span>
          <span class="chip-pill">Audio waveform + tags</span>
          <span class="chip-pill">Legacy media covered</span>
        </div>
      </article>

      <article class="panel-surface viewer-stage-card">
        <div class="viewer-stage-card__header">
          <div>
            <p class="eyebrow">Preview Stage</p>
            <h2>{{ selection?.file.name ?? 'Выбери файл для просмотра' }}</h2>
          </div>

          <div class="viewer-toolbar">
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="zoomOut"
            >
              -
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="zoomIn"
            >
              +
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="rotateLeft"
            >
              Left
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="rotateRight"
            >
              Right
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="resetViewportTransform"
            >
              Reset
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="!selection"
              @click="toggleFullscreen"
            >
              {{ isFullscreen ? 'Exit FS' : 'Fullscreen' }}
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="toggleTransparencyGrid"
            >
              {{ isTransparencyGridVisible ? 'Hide Grid' : 'Show Grid' }}
            </button>
          </div>
        </div>

        <div
          ref="previewStage"
          class="viewer-stage"
          :class="{
            'viewer-stage--checker': selection?.kind === 'image' && isTransparencyGridVisible,
          }"
        >
          <div v-if="isLoading" class="viewer-empty-state">
            <strong>Подготавливаю preview...</strong>
            <span>{{ loadingMessage }}</span>
          </div>

          <div v-else-if="errorMessage" class="viewer-empty-state viewer-empty-state--warning">
            <strong>Preview не собран</strong>
            <span>{{ errorMessage }}</span>
          </div>

          <div v-else-if="selection?.kind === 'image'" class="viewer-image-frame">
            <img
              ref="previewImage"
              class="viewer-image-frame__image"
              :src="selection.objectUrl"
              :alt="selection.file.name"
              :style="{ transform: viewportTransform }"
              @pointermove="handlePointerMove"
              @pointerleave="handlePointerLeave"
              @click="storeActiveSwatch"
            />
          </div>

          <div v-else-if="selection?.kind === 'video'" class="viewer-video-frame">
            <div class="video-stage-hud">
              <div class="document-stage-hud__meta">
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ selection.format.label }}
                </span>
                <span
                  v-for="metric in videoStageMetrics"
                  :key="metric"
                  class="chip-pill chip-pill--compact"
                >
                  {{ metric }}
                </span>
              </div>

              <div class="document-stage-hud__actions">
                <button class="action-button" type="button" @click="togglePlayback">
                  {{ isVideoPlaying ? 'Pause' : 'Play' }}
                </button>
                <button class="action-button" type="button" @click="stepFrame(-1)">-1f</button>
                <button class="action-button" type="button" @click="stepFrame(1)">+1f</button>
                <button class="action-button" type="button" @click="seekBy(-5)">-5s</button>
                <button class="action-button" type="button" @click="seekBy(5)">+5s</button>
                <button class="action-button" type="button" @click="toggleLoop">
                  {{ isLooping ? 'Loop On' : 'Loop Off' }}
                </button>
                <button class="action-button" type="button" @click="openSubtitlePicker">
                  Subtitles
                </button>
                <button class="action-button" type="button" @click="capturePoster">Poster</button>
                <button
                  class="action-button"
                  type="button"
                  :disabled="!canUsePictureInPicture"
                  @click="togglePictureInPicture"
                >
                  {{ isPictureInPictureActive ? 'Exit PiP' : 'PiP' }}
                </button>
              </div>
            </div>

            <video
              ref="previewVideo"
              class="viewer-video-frame__player"
              :src="selection.layout.objectUrl"
              :loop="isLooping"
              preload="metadata"
              playsinline
            >
              <track
                v-for="track in subtitleTracks"
                :key="track.id"
                :kind="track.kind"
                :label="track.label"
                :srclang="track.language"
                :src="track.objectUrl"
                :default="track.id === activeSubtitleTrackId"
              />
            </video>

            <div class="video-control-panel">
              <label class="video-progress">
                <span>{{ videoCurrentTimeLabel }}</span>
                <input
                  type="range"
                  min="0"
                  :max="videoDurationSeconds || selection.layout.durationSeconds || 0"
                  step="0.1"
                  :value="videoCurrentTime"
                  @input="onVideoSeekInput"
                />
                <span>{{ videoDurationLabel }}</span>
              </label>

              <div v-if="playbackMessage" class="video-tool-message">
                {{ playbackMessage }}
              </div>
              <div v-if="subtitleMessage" class="video-tool-message">
                {{ subtitleMessage }}
              </div>
              <div v-if="posterMessage" class="video-tool-message">
                {{ posterMessage }}
              </div>

              <div class="video-control-row">
                <label class="video-slider">
                  <span>Volume</span>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    :value="videoVolume"
                    @input="onVideoVolumeInput"
                  />
                </label>
                <button class="action-button" type="button" @click="toggleMute">
                  {{ isVideoMuted ? 'Unmute' : 'Mute' }}
                </button>
                <label class="video-rate">
                  <span>Speed</span>
                  <select :value="videoPlaybackRate" @change="onVideoRateChange">
                    <option v-for="rate in videoPlaybackRates" :key="rate" :value="rate">
                      {{ rate }}x
                    </option>
                  </select>
                </label>
                <label class="video-rate">
                  <span>Frame Rate</span>
                  <select :value="assumedFrameRate" @change="onVideoFrameRateChange">
                    <option v-for="rate in videoFrameRateOptions" :key="rate" :value="rate">
                      {{ rate }} fps
                    </option>
                  </select>
                </label>
                <span class="chip-pill chip-pill--compact">
                  {{ videoProgressPercent.toFixed(0) }}%
                </span>
                <span class="chip-pill chip-pill--compact"> {{ frameStepLabel }} / frame </span>
                <span class="chip-pill chip-pill--compact">
                  {{ approximateFrameNumber ? `Frame #${approximateFrameNumber}` : 'Frame n/a' }}
                </span>
              </div>

              <div class="video-control-row">
                <div class="viewer-dropzone__actions">
                  <button class="action-button" type="button" @click="copyCurrentTimestamp">
                    Copy Timestamp
                  </button>
                  <button class="action-button" type="button" @click="capturePoster">
                    Capture Poster
                  </button>
                  <button class="action-button" type="button" @click="openSubtitlePicker">
                    Add Subtitles
                  </button>
                  <button
                    class="action-button"
                    type="button"
                    :disabled="!subtitleTracks.length"
                    @click="clearSubtitleTracks"
                  >
                    Clear Subs
                  </button>
                </div>
                <div class="document-stage-hud__meta">
                  <span class="chip-pill chip-pill--compact">
                    {{ isLooping ? 'Looping' : 'Loop once' }}
                  </span>
                  <span class="chip-pill chip-pill--compact">
                    {{
                      activeSubtitleTrack ? `Active: ${activeSubtitleTrack.label}` : 'Subtitles off'
                    }}
                  </span>
                  <span class="chip-pill chip-pill--compact chip-pill--accent">
                    {{
                      posterCaptures.length
                        ? `${posterCaptures.length} posters`
                        : 'Poster rail empty'
                    }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="selection?.kind === 'audio'" class="viewer-audio-frame">
            <div class="video-stage-hud">
              <div class="document-stage-hud__meta">
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ selection.format.label }}
                </span>
                <span
                  v-for="metric in audioStageMetrics"
                  :key="metric"
                  class="chip-pill chip-pill--compact"
                >
                  {{ metric }}
                </span>
              </div>

              <div class="document-stage-hud__actions">
                <button class="action-button" type="button" @click="toggleAudioPlayback">
                  {{ isAudioPlaying ? 'Pause' : 'Play' }}
                </button>
                <button class="action-button" type="button" @click="seekAudioBy(-10)">-10s</button>
                <button class="action-button" type="button" @click="seekAudioBy(10)">+10s</button>
                <button class="action-button" type="button" @click="toggleAudioLoop">
                  {{ isAudioLooping ? 'Loop On' : 'Loop Off' }}
                </button>
                <button class="action-button" type="button" @click="copyAudioTimestamp">
                  Copy Time
                </button>
              </div>
            </div>

            <div class="audio-stage-shell">
              <div class="audio-stage-summary">
                <div v-if="selection.artworkDataUrl" class="audio-stage-artwork">
                  <img :src="selection.artworkDataUrl" :alt="`${selection.file.name} artwork`" />
                </div>
                <div v-else class="audio-stage-artwork audio-stage-artwork--empty">
                  <strong>{{ selection.format.label }}</strong>
                  <span>No embedded cover art</span>
                </div>

                <div class="audio-stage-copy">
                  <p class="eyebrow">Audio Stage</p>
                  <h3>{{ selection.file.name }}</h3>
                  <p>
                    Browser-native player, waveform rail и metadata inspector живут в одном
                    workspace, а legacy containers нормализуются в тот же контракт через backend
                    MEDIA_PREVIEW.
                  </p>
                </div>
              </div>

              <div class="audio-waveform" aria-label="Audio waveform preview">
                <div
                  v-for="(bucket, bucketIndex) in selection.layout.waveform"
                  :key="`wave-${bucketIndex}`"
                  class="audio-waveform__bar"
                  :style="{ height: `${Math.max(bucket * 100, 8)}%` }"
                ></div>
              </div>

              <audio
                ref="previewAudio"
                class="viewer-audio-frame__player"
                :src="selection.layout.objectUrl"
                :loop="isAudioLooping"
                preload="metadata"
              ></audio>

              <div class="video-control-panel">
                <label class="video-progress">
                  <span>{{ audioCurrentTimeLabel }}</span>
                  <input
                    type="range"
                    min="0"
                    :max="audioDurationSeconds || selection.layout.durationSeconds || 0"
                    step="0.1"
                    :value="audioCurrentTime"
                    @input="onAudioSeekInput"
                  />
                  <span>{{ audioDurationLabel }}</span>
                </label>

                <div v-if="audioPlaybackMessage" class="video-tool-message">
                  {{ audioPlaybackMessage }}
                </div>

                <div class="video-control-row">
                  <label class="video-slider">
                    <span>Volume</span>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.01"
                      :value="audioVolume"
                      @input="onAudioVolumeInput"
                    />
                  </label>
                  <button class="action-button" type="button" @click="toggleAudioMute">
                    {{ isAudioMuted ? 'Unmute' : 'Mute' }}
                  </button>
                  <label class="video-rate">
                    <span>Speed</span>
                    <select :value="audioPlaybackRate" @change="onAudioRateChange">
                      <option v-for="rate in audioPlaybackRates" :key="rate" :value="rate">
                        {{ rate }}x
                      </option>
                    </select>
                  </label>
                  <span class="chip-pill chip-pill--compact">
                    {{ audioProgressPercent.toFixed(0) }}%
                  </span>
                  <span class="chip-pill chip-pill--compact">
                    {{ isAudioLooping ? 'Looping' : 'Loop once' }}
                  </span>
                  <span class="chip-pill chip-pill--compact chip-pill--accent">
                    {{
                      selection.layout.waveform.length ? 'Waveform ready' : 'Waveform unavailable'
                    }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="selection?.kind === 'document'" class="viewer-document-frame">
            <div class="document-stage-hud">
              <div class="document-stage-hud__meta">
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ selection.format.label }}
                </span>
                <span class="chip-pill chip-pill--compact">
                  {{ documentModeLabel }}
                </span>
                <span
                  v-for="metric in documentStageMetrics"
                  :key="metric"
                  class="chip-pill chip-pill--compact"
                >
                  {{ metric }}
                </span>
              </div>

              <div class="document-stage-hud__actions">
                <button class="action-button" type="button" @click="copyDocumentText">
                  Copy Text
                </button>
                <button class="action-button" type="button" @click="downloadDocumentText">
                  Download Text
                </button>
                <button
                  class="action-button"
                  type="button"
                  :disabled="!documentQuery"
                  @click="clearDocumentSearch"
                >
                  Clear Search
                </button>
              </div>
            </div>

            <p v-if="documentActionMessage" class="document-action-message">
              {{ documentActionMessage }}
            </p>

            <iframe
              v-if="selection.layout.mode === 'pdf'"
              class="viewer-document-frame__embed"
              :src="selection.layout.objectUrl"
              :title="selection.file.name"
            ></iframe>

            <iframe
              v-else-if="selection.layout.mode === 'html'"
              class="viewer-document-frame__embed"
              sandbox=""
              :srcdoc="selection.layout.srcDoc"
              :title="selection.file.name"
            ></iframe>

            <div v-else-if="selection.layout.mode === 'table'" class="document-table">
              <div class="document-table__summary">
                <strong>{{ selection.layout.table.totalRows }} rows</strong>
                <span>{{ selection.layout.table.totalColumns }} columns</span>
              </div>
              <div class="document-table__scroll">
                <table>
                  <thead>
                    <tr>
                      <th v-for="column in selection.layout.table.columns" :key="column">
                        {{ column }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in selection.layout.table.rows" :key="rowIndex">
                      <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                        {{ cell || '—' }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <div v-else-if="selection.layout.mode === 'workbook'" class="document-workbook">
              <div class="document-workbook__tabs">
                <button
                  v-for="(sheet, sheetIndex) in selection.layout.sheets"
                  :key="sheet.id"
                  class="document-sheet-chip"
                  :class="{ 'document-sheet-chip--active': documentSheetIndex === sheetIndex }"
                  type="button"
                  @click="selectDocumentSheet(sheetIndex)"
                >
                  {{ sheet.name }}
                </button>
              </div>

              <div v-if="activeDocumentSheet" class="document-table">
                <div class="document-table__summary">
                  <strong>{{ activeDocumentSheet.table.totalRows }} rows</strong>
                  <span>{{ activeDocumentSheet.table.totalColumns }} columns</span>
                </div>
                <div class="document-table__scroll">
                  <table>
                    <thead>
                      <tr>
                        <th v-for="column in activeDocumentSheet.table.columns" :key="column">
                          {{ column }}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(row, rowIndex) in activeDocumentSheet.table.rows"
                        :key="`${activeDocumentSheet.id}-${rowIndex}`"
                      >
                        <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                          {{ cell || '—' }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <div v-else-if="selection.layout.mode === 'database'" class="document-database">
              <div class="document-workbook__tabs">
                <button
                  v-for="(table, tableIndex) in selection.layout.tables"
                  :key="table.id"
                  class="document-sheet-chip"
                  :class="{
                    'document-sheet-chip--active': documentDatabaseTableIndex === tableIndex,
                  }"
                  type="button"
                  @click="selectDocumentDatabaseTable(tableIndex)"
                >
                  {{ table.name }}
                </button>
              </div>

              <article v-if="activeDocumentDatabaseTable" class="document-database__schema">
                <div class="document-table__summary">
                  <strong>{{
                    activeDocumentDatabaseTable.rowCount == null
                      ? 'Rows: n/a'
                      : `${activeDocumentDatabaseTable.rowCount} rows`
                  }}</strong>
                  <span>{{ activeDocumentDatabaseTable.columns.length }} columns</span>
                </div>
                <pre>{{ activeDocumentDatabaseTable.schemaSql }}</pre>
              </article>

              <div v-if="activeDocumentDatabaseTable" class="document-table">
                <div class="document-table__summary">
                  <strong>{{ activeDocumentDatabaseTable.sample.totalRows }} rows</strong>
                  <span>{{ activeDocumentDatabaseTable.sample.totalColumns }} columns</span>
                </div>
                <div class="document-table__scroll">
                  <table>
                    <thead>
                      <tr>
                        <th
                          v-for="column in activeDocumentDatabaseTable.sample.columns"
                          :key="column"
                        >
                          {{ column }}
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(row, rowIndex) in activeDocumentDatabaseTable.sample.rows"
                        :key="`${activeDocumentDatabaseTable.id}-${rowIndex}`"
                      >
                        <td v-for="(cell, columnIndex) in row" :key="`${rowIndex}-${columnIndex}`">
                          {{ cell || '—' }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <p v-else class="viewer-panel__empty">В этой базе не найдено таблиц для preview.</p>
            </div>

            <div v-else-if="selection.layout.mode === 'slides'" class="document-slide-grid">
              <article
                v-if="activeDocumentSlide"
                class="document-slide-card document-slide-card--focus"
              >
                <div class="document-slide-card__meta">
                  <span class="chip-pill chip-pill--compact chip-pill--accent">
                    Focused Slide {{ documentSlideIndex + 1 }}
                  </span>
                </div>
                <h3>{{ activeDocumentSlide.title }}</h3>
                <ul v-if="activeDocumentSlide.bullets.length" class="document-slide-card__list">
                  <li v-for="bullet in activeDocumentSlide.bullets" :key="bullet">{{ bullet }}</li>
                </ul>
                <p v-else class="viewer-panel__empty">На выбранном слайде нет bullet-пунктов.</p>
              </article>

              <div class="document-slide-rail">
                <button
                  v-for="(slide, slideIndex) in selection.layout.slides"
                  :key="slide.id"
                  class="document-slide-chip"
                  :class="{ 'document-slide-chip--active': documentSlideIndex === slideIndex }"
                  type="button"
                  @click="selectDocumentSlide(slideIndex)"
                >
                  {{ slideIndex + 1 }} · {{ slide.title }}
                </button>
              </div>

              <article
                v-for="(slide, slideIndex) in selection.layout.slides"
                :key="slide.id"
                class="document-slide-card"
                :class="{ 'document-slide-card--selected': documentSlideIndex === slideIndex }"
                @click="selectDocumentSlide(slideIndex)"
              >
                <div class="document-slide-card__meta">
                  <span class="chip-pill chip-pill--compact">Slide {{ slideIndex + 1 }}</span>
                </div>
                <h3>{{ slide.title }}</h3>
                <ul v-if="slide.bullets.length" class="document-slide-card__list">
                  <li v-for="bullet in slide.bullets" :key="bullet">{{ bullet }}</li>
                </ul>
                <p v-else class="viewer-panel__empty">
                  На слайде не найден текстовый слой кроме заголовка.
                </p>
              </article>
            </div>

            <article v-else class="document-text">
              <p v-for="(paragraph, index) in selection.layout.paragraphs" :key="index">
                {{ paragraph }}
              </p>
            </article>
          </div>

          <div
            v-else-if="selection?.kind === 'unknown'"
            class="viewer-empty-state viewer-empty-state--warning"
          >
            <strong>{{ selection.headline }}</strong>
            <span>{{ selection.detail }}</span>
            <p>{{ selection.nextStep }}</p>
          </div>

          <div v-else class="viewer-empty-state">
            <strong>Viewer готов к первой загрузке</strong>
            <span>
              Сейчас уже работают image formats, весь document roadmap, video workbench и audio
              viewer для `mp3`, `wav`, `aac`, `flac`, `ogg`, `opus`, `aiff` с waveform и tag
              metadata.
            </span>
          </div>
        </div>

        <div class="viewer-stage-card__footer">
          <span v-if="selection?.kind === 'image'" class="chip-pill chip-pill--compact">
            Zoom: {{ zoom.toFixed(1) }}x
          </span>
          <span v-if="selection?.kind === 'image'" class="chip-pill chip-pill--compact">
            Rotation: {{ rotation }}deg
          </span>
          <span
            v-if="
              selection?.kind === 'image' ||
              selection?.kind === 'document' ||
              selection?.kind === 'video' ||
              selection?.kind === 'audio'
            "
            class="chip-pill chip-pill--compact chip-pill--accent"
          >
            {{ selection.previewLabel }}
          </span>
        </div>
      </article>
    </section>

    <section v-if="selection?.kind === 'image'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Color Lab</p>
        <h2>Color picker, loupe и palette capture</h2>

        <div v-if="activeSample" class="color-lab">
          <div class="color-lab__hero">
            <div
              class="color-lab__swatch"
              :style="{ backgroundColor: activeSample.rgb }"
              aria-hidden="true"
            ></div>
            <div class="color-lab__values">
              <strong>{{ activeSample.hex }}</strong>
              <span>{{ activeSample.rgb }}</span>
              <span>{{ activeSample.hsl }}</span>
              <span>Alpha: {{ activeSample.alpha }}</span>
              <span>Pixel: {{ activeSample.x }} x {{ activeSample.y }}</span>
            </div>
          </div>

          <div class="color-lab__loupe-row">
            <img class="color-lab__loupe" :src="activeSample.loupeDataUrl" alt="Loupe preview" />
            <div class="color-lab__actions">
              <button class="action-button" type="button" @click="copyActiveSample('hex')">
                Copy HEX
              </button>
              <button class="action-button" type="button" @click="copyActiveSample('rgb')">
                Copy RGB
              </button>
              <button class="action-button" type="button" @click="copyActiveSample('hsl')">
                Copy HSL
              </button>
              <button
                class="action-button action-button--accent"
                type="button"
                :disabled="!canUseTools"
                @click="storeActiveSwatch"
              >
                Save Swatch
              </button>
            </div>
          </div>
        </div>

        <p v-else class="viewer-panel__empty">
          Наведи курсор на изображение, чтобы получить цвет под пикселем, loupe и координаты.
        </p>

        <div v-if="swatches.length" class="swatch-grid">
          <article v-for="swatch in swatches" :key="swatch.id" class="swatch-card">
            <button
              class="swatch-card__preview"
              type="button"
              :style="{ backgroundColor: swatch.sample.rgb }"
              :title="`Remove ${swatch.sample.hex}`"
              @click="removeSwatch(swatch.id)"
            ></button>
            <strong>{{ swatch.sample.hex }}</strong>
            <span>{{ swatch.sample.rgb }}</span>
          </article>
        </div>

        <div v-if="histogram" class="histogram-panel">
          <div class="histogram-panel__legend">
            <span class="histogram-dot histogram-dot--red">Red</span>
            <span class="histogram-dot histogram-dot--green">Green</span>
            <span class="histogram-dot histogram-dot--blue">Blue</span>
            <span class="histogram-dot histogram-dot--luma">Luma</span>
          </div>
          <div class="histogram-panel__chart">
            <div
              v-for="(value, index) in histogram.luminance"
              :key="`luma-${index}`"
              class="histogram-bar histogram-bar--luma"
              :style="{ height: `${Math.max(value * 100, 4)}%` }"
            ></div>
            <div
              v-for="(value, index) in histogram.red"
              :key="`red-${index}`"
              class="histogram-bar histogram-bar--red"
              :style="{ height: `${Math.max(value * 100, 4)}%` }"
            ></div>
            <div
              v-for="(value, index) in histogram.green"
              :key="`green-${index}`"
              class="histogram-bar histogram-bar--green"
              :style="{ height: `${Math.max(value * 100, 4)}%` }"
            ></div>
            <div
              v-for="(value, index) in histogram.blue"
              :key="`blue-${index}`"
              class="histogram-bar histogram-bar--blue"
              :style="{ height: `${Math.max(value * 100, 4)}%` }"
            ></div>
          </div>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Metadata</p>
        <h2>Summary и быстрый inspector</h2>
        <dl class="facts-grid">
          <template v-for="fact in selectionFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </template>
        </dl>
        <p v-if="!selectionFacts.length" class="viewer-panel__empty">
          После выбора файла сюда подтянутся базовые metadata и текущий preview path.
        </p>
        <img
          v-if="metadataThumbnail"
          class="metadata-thumbnail"
          :src="metadataThumbnail"
          alt="Embedded thumbnail"
        />
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Inspector</p>
        <h2>EXIF, ICC и другие metadata группы</h2>

        <label class="metadata-search">
          <span>Filter tags</span>
          <input v-model="metadataQuery" type="text" placeholder="Orientation, ICC, Lens..." />
        </label>

        <div class="metadata-group-stack">
          <article v-for="group in filteredMetadataGroups" :key="group.id" class="metadata-group">
            <div class="metadata-group__header">
              <strong>{{ group.label }}</strong>
              <span class="chip-pill chip-pill--compact">{{ group.entries.length }} tags</span>
            </div>
            <dl class="metadata-group__list">
              <template v-for="entry in group.entries" :key="`${group.id}-${entry.label}`">
                <dt>{{ entry.label }}</dt>
                <dd>{{ entry.value }}</dd>
              </template>
            </dl>
          </article>
        </div>

        <p v-if="!filteredMetadataGroups.length" class="viewer-panel__empty">
          По этому фильтру ничего не найдено.
        </p>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Metadata Editor</p>
        <h2>Common metadata fields и экспорт изменений</h2>

        <form class="metadata-editor" @submit.prevent="saveMetadataDraft">
          <label>
            <span>Description</span>
            <textarea
              v-model="metadataDraft.description"
              rows="4"
              placeholder="Image description / title"
            ></textarea>
          </label>

          <label>
            <span>Artist</span>
            <input v-model="metadataDraft.artist" type="text" placeholder="Author / photographer" />
          </label>

          <label>
            <span>Copyright</span>
            <input v-model="metadataDraft.copyright" type="text" placeholder="Copyright notice" />
          </label>

          <label>
            <span>Captured at</span>
            <input v-model="metadataDraft.capturedAt" type="datetime-local" />
          </label>

          <div class="metadata-editor__footer">
            <p class="metadata-editor__mode">
              {{
                metadataEmbeddingAvailable
                  ? 'Для JPEG backend соберёт новый файл с обновлёнными EXIF-полями.'
                  : 'Для этого формата backend соберёт sidecar JSON с metadata patch.'
              }}
            </p>
            <button
              class="action-button action-button--accent"
              type="submit"
              :disabled="isSavingMetadata"
            >
              {{ isSavingMetadata ? 'Building...' : 'Export Metadata' }}
            </button>
          </div>
        </form>

        <p v-if="metadataSaveMessage" class="metadata-editor__message">
          {{ metadataSaveMessage }}
        </p>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Capability Map</p>
        <h2>Форматы и runtime-path внутри image viewer</h2>
        <div class="capability-columns">
          <div class="format-grid">
            <article
              v-for="format in browserNativeFormats"
              :key="format.extension"
              class="format-card format-card--native"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact">{{ format.statusLabel }}</span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>

          <div class="format-grid">
            <article
              v-for="format in serverImageFormats"
              :key="format.extension"
              class="format-card format-card--pipeline"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ format.statusLabel }}
                </span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>
        </div>
        <p v-if="!plannedMediaFormats.length" class="viewer-panel__empty">
          Для video roadmap в текущем срезе больше нет planned-only слотов: все заявленные форматы
          уже сводятся к native path или server-assisted media preview внутри одного workspace.
        </p>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'video'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Video Summary</p>
        <h2>Playback facts, warnings и browser-native metadata</h2>
        <div class="document-summary-row">
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.format.label
          }}</span>
          <span class="chip-pill chip-pill--compact"
            >{{ videoCurrentTimeLabel }} / {{ videoDurationLabel }}</span
          >
          <span class="chip-pill chip-pill--compact">{{
            isVideoPlaying ? 'Playing' : 'Paused'
          }}</span>
          <span class="chip-pill chip-pill--compact">{{ isLooping ? 'Loop On' : 'Loop Off' }}</span>
        </div>
        <dl class="facts-grid">
          <template v-for="fact in selectionFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </template>
        </dl>
        <div v-if="videoWarnings.length" class="warning-stack">
          <article v-for="warning in videoWarnings" :key="warning" class="warning-card">
            {{ warning }}
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Precision</p>
        <h2>Frame stepping, rate assumptions и session tooling</h2>
        <div class="outline-stack">
          <article v-for="card in videoMetadataCards" :key="card.label" class="outline-card">
            <strong>{{ card.label }}</strong>
            <span>{{ card.value }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Subtitles</p>
        <h2>Sidecar tracks, cue count и quick switching</h2>
        <div class="viewer-dropzone__actions">
          <button
            class="action-button action-button--accent"
            type="button"
            @click="openSubtitlePicker"
          >
            Add Subtitle File
          </button>
          <button
            class="action-button"
            type="button"
            :class="{ 'action-button--accent': activeSubtitleTrackId === 'off' }"
            @click="setActiveSubtitleTrack('off')"
          >
            Turn Off
          </button>
          <button
            class="action-button"
            type="button"
            :disabled="!subtitleTracks.length"
            @click="clearSubtitleTracks"
          >
            Clear All
          </button>
        </div>
        <div v-if="subtitleTracks.length" class="subtitle-track-grid">
          <article
            v-for="track in subtitleTracks"
            :key="track.id"
            class="subtitle-track-card"
            :class="{ 'subtitle-track-card--active': activeSubtitleTrackId === track.id }"
          >
            <button
              class="subtitle-track-card__select"
              type="button"
              @click="setActiveSubtitleTrack(track.id)"
            >
              <strong>{{ track.label }}</strong>
              <span
                >{{ track.language.toUpperCase() }} · {{ track.cueCount }} cues ·
                {{ track.format.toUpperCase() }}</span
              >
            </button>
            <button
              class="subtitle-track-card__action"
              type="button"
              @click="removeSubtitleTrack(track.id)"
            >
              Remove
            </button>
          </article>
        </div>
        <p v-else class="viewer-panel__empty">
          В viewer можно подложить `.vtt` или `.srt` как session subtitle tracks без пересборки
          самого файла.
        </p>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Poster Lab</p>
        <h2>Frame-derived stills, timestamp export и quick review rail</h2>
        <div class="viewer-dropzone__actions">
          <button class="action-button action-button--accent" type="button" @click="capturePoster">
            Capture Current Frame
          </button>
          <button class="action-button" type="button" @click="copyCurrentTimestamp">
            Copy Timestamp
          </button>
        </div>
        <div v-if="posterCaptures.length" class="poster-capture-grid">
          <article v-for="capture in posterCaptures" :key="capture.id" class="poster-capture-card">
            <img :src="capture.objectUrl" :alt="capture.fileName" />
            <div class="poster-capture-card__meta">
              <strong>{{ capture.timeLabel }}</strong>
              <span>{{ capture.width }} x {{ capture.height }}</span>
            </div>
            <div class="viewer-dropzone__actions">
              <button class="action-button" type="button" @click="seekTo(capture.timeSeconds)">
                Jump
              </button>
              <button
                class="action-button"
                type="button"
                @click="downloadPosterCapture(capture.id)"
              >
                Download
              </button>
              <button class="action-button" type="button" @click="removePosterCapture(capture.id)">
                Remove
              </button>
            </div>
          </article>
        </div>
        <p v-else class="viewer-panel__empty">
          Poster rail пока пуст. Захват кадра полезен для cover-image, handoff в converter и быстрых
          visual checks без отдельного export-пайплайна.
        </p>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Shortcuts</p>
        <h2>Keyboard flow для точной навигации</h2>
        <div class="outline-stack">
          <article v-for="shortcut in videoShortcutHints" :key="shortcut.keys" class="outline-card">
            <strong>{{ shortcut.keys }}</strong>
            <span>{{ shortcut.description }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Capability Map</p>
        <h2>Video runtime и текущие playback paths</h2>
        <div class="capability-columns">
          <div class="format-grid">
            <article
              v-for="format in activeMediaFormats"
              :key="format.extension"
              class="format-card format-card--pipeline"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ format.statusLabel }}
                </span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>

          <div class="format-grid">
            <article
              v-for="format in plannedMediaFormats"
              :key="format.extension"
              class="format-card format-card--planned"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact">{{ format.statusLabel }}</span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>
        </div>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'audio'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Audio Summary</p>
        <h2>Playback facts, warnings и technical metadata</h2>
        <div class="document-summary-row">
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.format.label
          }}</span>
          <span class="chip-pill chip-pill--compact"
            >{{ audioCurrentTimeLabel }} / {{ audioDurationLabel }}</span
          >
          <span class="chip-pill chip-pill--compact">{{
            isAudioPlaying ? 'Playing' : 'Paused'
          }}</span>
          <span class="chip-pill chip-pill--compact">{{
            isAudioLooping ? 'Loop On' : 'Loop Off'
          }}</span>
        </div>
        <dl class="facts-grid">
          <template v-for="fact in selectionFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </template>
        </dl>
        <div v-if="audioWarnings.length" class="warning-stack">
          <article v-for="warning in audioWarnings" :key="warning" class="warning-card">
            {{ warning }}
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Playback</p>
        <h2>Waveform, speed control и session flow</h2>
        <div class="outline-stack">
          <article v-for="card in audioMetadataCards" :key="card.label" class="outline-card">
            <strong>{{ card.label }}</strong>
            <span>{{ card.value }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Metadata</p>
        <h2>Artwork, tags и quick filter</h2>

        <div class="audio-metadata-hero">
          <div v-if="selection.artworkDataUrl" class="audio-metadata-hero__artwork">
            <img :src="selection.artworkDataUrl" :alt="`${selection.file.name} artwork`" />
          </div>
          <div v-else class="audio-metadata-hero__artwork audio-metadata-hero__artwork--empty">
            <strong>{{ selection.format.label }}</strong>
            <span>No cover art</span>
          </div>

          <div class="audio-metadata-hero__copy">
            <span class="chip-pill chip-pill--compact chip-pill--accent">
              {{ filteredAudioMetadataGroups.length }} groups
            </span>
            <p>
              Viewer поднимает common tags, technical summary и native tag groups без отдельного
              backend round-trip.
            </p>
          </div>
        </div>

        <label class="metadata-search">
          <span>Filter tags</span>
          <input v-model="audioMetadataQuery" type="text" placeholder="artist, album, codec..." />
        </label>

        <div class="metadata-group-stack">
          <article
            v-for="group in filteredAudioMetadataGroups"
            :key="group.id"
            class="metadata-group"
          >
            <div class="metadata-group__header">
              <strong>{{ group.label }}</strong>
              <span class="chip-pill chip-pill--compact">{{ group.entries.length }} tags</span>
            </div>
            <dl class="metadata-group__list">
              <template v-for="entry in group.entries" :key="`${group.id}-${entry.label}`">
                <dt>{{ entry.label }}</dt>
                <dd>{{ entry.value }}</dd>
              </template>
            </dl>
          </article>
        </div>

        <p v-if="!filteredAudioMetadataGroups.length" class="viewer-panel__empty">
          По этому фильтру ничего не найдено.
        </p>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Shortcuts</p>
        <h2>Keyboard flow для аудио-навигации</h2>
        <div class="outline-stack">
          <article v-for="shortcut in audioShortcutHints" :key="shortcut.keys" class="outline-card">
            <strong>{{ shortcut.keys }}</strong>
            <span>{{ shortcut.description }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Capability Map</p>
        <h2>Audio runtime и текущие playback paths</h2>
        <div class="capability-columns">
          <div class="format-grid">
            <article
              v-for="format in activeAudioFormats"
              :key="format.extension"
              class="format-card format-card--pipeline"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ format.statusLabel }}
                </span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>

          <div class="format-grid">
            <article
              v-for="format in plannedAudioFormats"
              :key="format.extension"
              class="format-card format-card--planned"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact">{{ format.statusLabel }}</span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>
        </div>
        <p v-if="!plannedAudioFormats.length" class="viewer-panel__empty">
          Для audio roadmap в этой итерации больше нет planned-only слотов: все заявленные форматы
          теперь идут через реальные playback strategies.
        </p>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'document'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Document Summary</p>
        <h2>Статистика, warnings и preview facts</h2>
        <div class="document-summary-row">
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.format.label
          }}</span>
          <span class="chip-pill chip-pill--compact">{{ documentModeLabel }}</span>
          <span class="chip-pill chip-pill--compact">Matches: {{ documentMatches.length }}</span>
        </div>
        <dl class="facts-grid">
          <template v-for="fact in selectionFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </template>
        </dl>
        <div v-if="documentWarnings.length" class="warning-stack">
          <article v-for="warning in documentWarnings" :key="warning" class="warning-card">
            {{ warning }}
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Quick Find</p>
        <h2>Search layer поверх нормализованного document text</h2>
        <label class="metadata-search">
          <span>Search document</span>
          <input v-model="documentQuery" type="text" :placeholder="documentSearchPlaceholder" />
        </label>
        <div class="document-search-toolbar">
          <span class="chip-pill chip-pill--compact">
            {{ documentQuery ? `${documentMatches.length} matches` : 'Search ready' }}
          </span>
          <span v-if="activeDocumentMatch" class="chip-pill chip-pill--compact chip-pill--accent">
            Match {{ activeDocumentMatchIndex + 1 }}
          </span>
        </div>
        <div v-if="documentMatches.length" class="search-match-stack">
          <button
            v-for="(match, matchIndex) in documentMatches"
            :key="match.id"
            class="search-match-card"
            :class="{ 'search-match-card--active': activeDocumentMatchIndex === matchIndex }"
            type="button"
            @click="focusDocumentMatch(matchIndex)"
          >
            <strong>Result {{ matchIndex + 1 }}</strong>
            <span>{{ match.excerpt }}</span>
          </button>
        </div>
        <p v-else class="viewer-panel__empty">
          {{
            documentQuery
              ? 'Совпадения не найдены.'
              : 'Search panel работает для legacy, OOXML, archive/reflow и SQLite document adapters.'
          }}
        </p>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Structure</p>
        <h2>Outline, table hints и content slices</h2>

        <div v-if="documentOutline.length" class="outline-stack">
          <article
            v-for="heading in documentOutline"
            :key="heading.id"
            class="outline-card"
            :style="{ paddingLeft: `${16 + (heading.level - 1) * 12}px` }"
          >
            <strong>H{{ heading.level }}</strong>
            <span>{{ heading.label }}</span>
          </article>
        </div>

        <div v-else-if="documentTable" class="outline-stack">
          <article v-for="column in documentTable.columns" :key="column" class="outline-card">
            <strong>Column</strong>
            <span>{{ column }}</span>
          </article>
        </div>

        <div v-else-if="documentWorkbook" class="outline-stack">
          <article
            v-for="(sheet, sheetIndex) in documentWorkbook.sheets"
            :key="sheet.id"
            class="outline-card outline-card--interactive"
            :class="{ 'outline-card--active': documentSheetIndex === sheetIndex }"
            @click="selectDocumentSheet(sheetIndex)"
          >
            <strong>Sheet</strong>
            <span>{{ sheet.name }} · {{ sheet.table.totalRows }} rows</span>
          </article>
        </div>

        <div v-else-if="documentSlides.length" class="outline-stack">
          <article
            v-for="(slide, slideIndex) in documentSlides"
            :key="slide.id"
            class="outline-card outline-card--interactive"
            :class="{ 'outline-card--active': documentSlideIndex === slideIndex }"
            @click="selectDocumentSlide(slideIndex)"
          >
            <strong>S{{ slideIndex + 1 }}</strong>
            <span>{{ slide.title }}</span>
          </article>
        </div>

        <div v-else-if="documentDatabase" class="outline-stack">
          <article
            v-for="(table, tableIndex) in documentDatabase.tables"
            :key="table.id"
            class="outline-card outline-card--interactive"
            :class="{ 'outline-card--active': documentDatabaseTableIndex === tableIndex }"
            @click="selectDocumentDatabaseTable(tableIndex)"
          >
            <strong>Table</strong>
            <span>{{
              table.rowCount == null ? table.name : `${table.name} · ${table.rowCount} rows`
            }}</span>
          </article>
        </div>

        <div v-else-if="documentParagraphs.length" class="excerpt-stack">
          <article
            v-for="(paragraph, index) in documentParagraphs"
            :key="index"
            class="excerpt-card"
          >
            {{ paragraph }}
          </article>
        </div>

        <p v-else class="viewer-panel__empty">
          Для этого document mode структурная панель пока ограничена summary и search layer.
        </p>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Capability Map</p>
        <h2>Document foundation и текущие preview paths</h2>
        <div class="capability-columns">
          <div class="format-grid">
            <article
              v-for="format in activeDocumentFormats"
              :key="format.extension"
              class="format-card format-card--pipeline"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact chip-pill--accent">
                  {{ format.statusLabel }}
                </span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>

          <div class="format-grid">
            <article
              v-for="format in plannedDocumentFormats"
              :key="format.extension"
              class="format-card format-card--planned"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact">{{ format.statusLabel }}</span>
              </div>
              <p>{{ format.notes }}</p>
            </article>
          </div>
        </div>
        <p v-if="!plannedDocumentFormats.length" class="viewer-panel__empty">
          Для document roadmap в этой итерации больше нет planned-only слотов: все заявленные
          форматы теперь идут через реальные preview strategies.
        </p>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Architecture</p>
        <h2>Document viewer строится поверх того же workspace, а не рядом с ним.</h2>
        <div class="architecture-grid">
          <article class="architecture-card">
            <strong>Format Registry</strong>
            <p>
              Теперь знает не только image family, но и весь document capability map без
              foundation-only хвостов в текущем срезе.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Strategy Runtime</strong>
            <p>
              Каждый документ идёт через свой adapter, но результат сводится к одному document
              selection contract.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Layout Modes</strong>
            <p>
              PDF embed, plain text, tabular preview, sandbox HTML и database inspection живут как
              mode-подтипы одной модели.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Search Layer</strong>
            <p>
              Quick find работает по нормализованному text layer, а не зависит от конкретного
              renderer или DOM.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Capability Honesty</strong>
            <p>
              Legacy, archive и SQLite paths честно показывают упрощённый preview там, где faithful
              render неразумен, вместо ложной promise-поддержки.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Shared Workspace</strong>
            <p>
              Маршрут остаётся один: stage, fullscreen, drag-and-drop и summary-панели
              переиспользуются между семьями.
            </p>
          </article>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.viewer-workspace {
  display: grid;
  gap: 22px;
}

.viewer-hero-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.25fr);
  gap: 22px;
}

.viewer-intro,
.viewer-stage-card,
.viewer-panel {
  padding: 28px;
}

h1,
h2 {
  margin: 16px 0 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  letter-spacing: -0.04em;
}

h1 {
  max-width: 11ch;
  font-size: clamp(2.6rem, 4.4vw, 4.8rem);
  line-height: 0.95;
}

h2 {
  font-size: clamp(1.7rem, 2.8vw, 2.5rem);
  line-height: 0.98;
}

.lead {
  margin: 22px 0 0;
  color: var(--text-soft);
  font-size: 1.04rem;
}

.viewer-dropzone {
  display: grid;
  gap: 18px;
  margin-top: 28px;
  padding: 22px;
  border: 1px dashed rgba(29, 92, 85, 0.28);
  border-radius: var(--radius-xl);
  background: linear-gradient(145deg, rgba(255, 249, 241, 0.7), rgba(228, 219, 205, 0.58));
  box-shadow: var(--shadow-pressed);
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.viewer-dropzone--active {
  transform: translateY(-2px);
  border-color: rgba(29, 92, 85, 0.54);
  box-shadow: var(--shadow-floating);
}

.viewer-dropzone__copy {
  display: grid;
  gap: 8px;
}

.viewer-dropzone__copy strong {
  color: var(--text-strong);
  font-size: 1.1rem;
}

.viewer-dropzone__copy span,
.viewer-panel__empty,
.format-card p,
.architecture-card p,
.metadata-editor__mode {
  color: var(--text-soft);
}

.viewer-dropzone__actions,
.signal-row,
.viewer-toolbar,
.viewer-stage-card__footer,
.color-lab__actions,
.histogram-panel__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.viewer-stage-card {
  display: grid;
  gap: 20px;
}

.viewer-stage-card__header,
.color-lab__hero,
.color-lab__loupe-row,
.metadata-group__header,
.format-card__meta,
.metadata-editor__footer,
.document-table__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.viewer-stage-card__header h2 {
  max-width: 16ch;
}

.viewer-stage {
  display: grid;
  min-height: 520px;
  place-items: center;
  padding: 20px;
  border-radius: calc(var(--radius-2xl) - 8px);
  background:
    radial-gradient(circle at top right, rgba(255, 196, 129, 0.18), transparent 26%),
    linear-gradient(155deg, rgba(255, 251, 245, 0.8), rgba(227, 216, 201, 0.86));
  box-shadow: var(--shadow-pressed);
}

.viewer-stage--checker {
  background:
    linear-gradient(45deg, rgba(29, 92, 85, 0.08) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(29, 92, 85, 0.08) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(255, 203, 148, 0.16) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(255, 203, 148, 0.16) 75%),
    linear-gradient(155deg, rgba(255, 251, 245, 0.8), rgba(227, 216, 201, 0.86));
  background-size:
    24px 24px,
    24px 24px,
    24px 24px,
    24px 24px,
    auto;
  background-position:
    0 0,
    0 12px,
    12px -12px,
    -12px 0,
    0 0;
}

.viewer-image-frame,
.viewer-document-frame,
.viewer-video-frame,
.viewer-audio-frame {
  display: grid;
  width: 100%;
  min-height: 480px;
  place-items: start center;
  overflow: auto;
}

.viewer-image-frame {
  place-items: center;
}

.viewer-video-frame {
  gap: 16px;
}

.viewer-audio-frame {
  gap: 16px;
}

.viewer-image-frame__image {
  max-width: min(100%, 920px);
  max-height: 72vh;
  object-fit: contain;
  border-radius: 24px;
  box-shadow:
    0 22px 46px rgba(20, 48, 45, 0.18),
    0 2px 0 rgba(255, 255, 255, 0.6);
  transform-origin: center center;
  transition: transform 180ms ease;
}

.viewer-video-frame__player {
  width: min(100%, 980px);
  max-height: 68vh;
  border-radius: 24px;
  background: rgba(17, 27, 28, 0.94);
  box-shadow:
    0 22px 46px rgba(20, 48, 45, 0.24),
    0 2px 0 rgba(255, 255, 255, 0.32);
}

.viewer-audio-frame__player {
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.audio-stage-shell,
.audio-stage-summary,
.audio-metadata-hero {
  display: grid;
  gap: 16px;
}

.audio-stage-shell {
  width: min(100%, 980px);
  padding: 22px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.22), transparent 28%),
    linear-gradient(145deg, rgba(255, 251, 245, 0.94), rgba(230, 220, 205, 0.94));
  box-shadow: var(--shadow-pressed);
}

.audio-stage-summary,
.audio-metadata-hero {
  grid-template-columns: minmax(180px, 220px) minmax(0, 1fr);
  align-items: center;
}

.audio-stage-artwork,
.audio-metadata-hero__artwork {
  display: grid;
  place-items: center;
  min-height: 180px;
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  overflow: hidden;
}

.audio-stage-artwork img,
.audio-metadata-hero__artwork img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 18px;
  box-shadow:
    0 18px 32px rgba(20, 48, 45, 0.18),
    0 2px 0 rgba(255, 255, 255, 0.52);
}

.audio-stage-artwork--empty,
.audio-metadata-hero__artwork--empty {
  align-content: center;
  justify-items: center;
  text-align: center;
  color: var(--text-soft);
}

.audio-stage-copy,
.audio-metadata-hero__copy {
  display: grid;
  gap: 12px;
}

.audio-stage-copy h3 {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(1.4rem, 2.4vw, 2rem);
  letter-spacing: -0.03em;
}

.audio-stage-copy p,
.audio-metadata-hero__copy p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.audio-waveform {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8px, 1fr));
  align-items: end;
  gap: 6px;
  min-height: 140px;
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(29, 92, 85, 0.08), rgba(255, 203, 148, 0.12)),
    rgba(255, 250, 242, 0.88);
  box-shadow: var(--shadow-pressed);
}

.audio-waveform__bar {
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 157, 97, 0.98), rgba(29, 92, 85, 0.92));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.32),
    0 12px 24px rgba(20, 48, 45, 0.12);
}

.viewer-document-frame__embed {
  width: 100%;
  min-height: 70vh;
  border: 0;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: var(--shadow-pressed);
}

.document-stage-hud,
.document-stage-hud__meta,
.document-stage-hud__actions,
.document-summary-row,
.document-search-toolbar,
.document-slide-rail,
.video-stage-hud,
.video-control-row,
.video-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.document-stage-hud {
  width: 100%;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.document-stage-hud__meta {
  align-items: center;
}

.document-stage-hud__actions {
  justify-content: flex-end;
}

.document-action-message {
  width: 100%;
  margin: 0 0 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(255, 246, 232, 0.9), rgba(229, 218, 201, 0.94));
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}

.video-stage-hud {
  width: 100%;
  align-items: flex-start;
  justify-content: space-between;
}

.video-control-panel {
  display: grid;
  gap: 12px;
  width: min(100%, 980px);
  padding: 18px;
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.18), transparent 28%),
    rgba(255, 250, 242, 0.9);
  box-shadow: var(--shadow-pressed);
}

.video-tool-message {
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(255, 246, 232, 0.9), rgba(229, 218, 201, 0.94));
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}

.video-progress,
.video-slider,
.video-rate {
  align-items: center;
}

.video-progress {
  width: 100%;
}

.video-progress span:first-child,
.video-progress span:last-child {
  min-width: 64px;
  color: var(--text-soft);
  font-weight: 700;
}

.video-progress input,
.video-slider input {
  width: 100%;
}

.video-control-row {
  align-items: center;
  justify-content: space-between;
}

.video-slider,
.video-rate {
  display: flex;
  gap: 10px;
  color: var(--text-main);
}

.video-rate select {
  min-height: 38px;
  padding: 8px 12px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}

.document-text,
.document-table,
.document-workbook,
.document-database,
.document-slide-grid,
.subtitle-track-grid,
.poster-capture-grid,
.warning-stack,
.search-match-stack,
.outline-stack,
.excerpt-stack,
.color-lab,
.metadata-group-stack,
.metadata-editor,
.format-grid,
.architecture-grid {
  display: grid;
  gap: 14px;
}

.subtitle-track-grid,
.poster-capture-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.subtitle-track-card,
.poster-capture-card {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
}

.subtitle-track-card--active {
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.72),
    0 18px 32px rgba(20, 48, 45, 0.16);
  outline: 1px solid rgba(29, 92, 85, 0.22);
}

.subtitle-track-card__select,
.subtitle-track-card__action {
  border: 0;
  border-radius: 16px;
  background: transparent;
  color: inherit;
}

.subtitle-track-card__select {
  display: grid;
  gap: 6px;
  padding: 0;
  text-align: left;
  cursor: pointer;
}

.subtitle-track-card__action {
  justify-self: start;
  padding: 10px 14px;
  background: rgba(29, 92, 85, 0.1);
  box-shadow: var(--shadow-pressed);
  cursor: pointer;
}

.subtitle-track-card__select span,
.poster-capture-card__meta span {
  color: var(--text-soft);
}

.poster-capture-card img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: 18px;
  box-shadow:
    0 16px 30px rgba(20, 48, 45, 0.18),
    0 2px 0 rgba(255, 255, 255, 0.54);
}

.poster-capture-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.document-text {
  width: 100%;
}

.document-text p {
  margin: 0;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}

.document-table {
  width: 100%;
}

.document-workbook {
  width: 100%;
}

.document-database {
  width: 100%;
}

.document-workbook__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.document-sheet-chip {
  min-height: 38px;
  padding: 8px 14px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-soft);
  font-weight: 700;
  cursor: pointer;
  transition: transform 160ms ease;
}

.document-sheet-chip:hover,
.document-slide-chip:hover,
.outline-card--interactive:hover,
.search-match-card:hover {
  transform: translateY(-1px);
}

.document-sheet-chip--active {
  color: var(--accent-cool-strong);
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.5), transparent 36%),
    rgba(255, 250, 242, 0.92);
}

.document-table__scroll {
  overflow: auto;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-pressed);
}

.document-database__schema {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at top right, rgba(29, 92, 85, 0.1), transparent 32%),
    rgba(255, 250, 242, 0.88);
  box-shadow: var(--shadow-pressed);
}

.document-database__schema pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-main);
  font-size: 0.95rem;
}

.document-table table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 250, 242, 0.84);
}

.document-table th,
.document-table td {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(16, 36, 38, 0.08);
  text-align: left;
  vertical-align: top;
}

.document-table th {
  position: sticky;
  top: 0;
  background: rgba(240, 230, 216, 0.96);
  color: var(--text-strong);
}

.document-slide-grid {
  width: 100%;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.document-slide-card {
  display: grid;
  gap: 14px;
  min-height: 240px;
  padding: 22px;
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at top right, rgba(255, 203, 148, 0.24), transparent 32%),
    var(--surface-muted);
  box-shadow: var(--shadow-pressed);
  cursor: pointer;
}

.document-slide-card--focus {
  grid-column: 1 / -1;
  min-height: 0;
  cursor: default;
}

.document-slide-card--selected,
.outline-card--active {
  background:
    radial-gradient(circle at top left, rgba(29, 92, 85, 0.12), transparent 30%),
    var(--surface-muted);
}

.document-slide-card h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: 1.45rem;
  line-height: 1.02;
}

.document-slide-card__meta {
  display: flex;
  justify-content: flex-start;
}

.document-slide-card__list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-main);
}

.document-slide-chip {
  min-height: 36px;
  padding: 8px 12px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-soft);
  cursor: pointer;
  text-align: left;
  transition: transform 160ms ease;
}

.document-slide-chip--active {
  color: var(--accent-cool-strong);
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.5), transparent 36%),
    rgba(255, 250, 242, 0.92);
}

.viewer-empty-state {
  display: grid;
  max-width: 46ch;
  gap: 10px;
  text-align: center;
}

.viewer-empty-state strong {
  color: var(--text-strong);
  font-size: 1.2rem;
}

.viewer-empty-state span,
.viewer-empty-state p {
  margin: 0;
  color: var(--text-soft);
}

.viewer-empty-state--warning strong {
  color: var(--accent-coral);
}

.viewer-detail-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 20px;
}

.viewer-panel {
  display: grid;
  gap: 18px;
  grid-column: span 4;
  align-content: start;
}

.viewer-panel--wide {
  grid-column: span 12;
}

.facts-grid,
.metadata-group__list {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1fr);
  gap: 12px 18px;
  margin: 0;
}

.facts-grid dt,
.facts-grid dd,
.metadata-group__list dt,
.metadata-group__list dd {
  margin: 0;
  padding: 12px 14px;
  border-radius: 18px;
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.facts-grid dt,
.metadata-group__list dt {
  color: var(--text-main);
  font-weight: 700;
}

.warning-card,
.search-match-card,
.outline-card,
.excerpt-card,
.swatch-card,
.format-card,
.architecture-card,
.metadata-group,
.histogram-panel,
.metadata-editor {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.warning-card {
  color: var(--accent-cool-strong);
}

.search-match-card {
  border: 0;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
  transition: transform 160ms ease;
}

.search-match-card--active {
  background:
    radial-gradient(circle at top left, rgba(29, 92, 85, 0.12), transparent 30%),
    var(--surface-muted);
}

.outline-card {
  grid-template-columns: auto 1fr;
  align-items: center;
}

.outline-card--interactive {
  cursor: pointer;
  transition: transform 160ms ease;
}

.outline-card strong,
.format-card__meta strong,
.architecture-card strong,
.metadata-group__header strong,
.color-lab__values strong {
  color: var(--text-strong);
  font-size: 1.02rem;
}

.color-lab__swatch {
  width: 132px;
  min-width: 132px;
  border-radius: 24px;
  box-shadow: var(--shadow-pressed);
  background-image:
    linear-gradient(45deg, rgba(29, 92, 85, 0.06) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(29, 92, 85, 0.06) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(255, 203, 148, 0.14) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(255, 203, 148, 0.14) 75%);
  background-size: 20px 20px;
}

.color-lab__values {
  display: grid;
  flex: 1;
  gap: 8px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.color-lab__loupe {
  width: 132px;
  height: 132px;
  flex: none;
  border-radius: 24px;
  object-fit: cover;
  box-shadow: var(--shadow-pressed);
  background: var(--bg-contrast);
}

.swatch-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(124px, 1fr));
  gap: 12px;
}

.swatch-card__preview {
  width: 100%;
  min-height: 68px;
  border: 0;
  border-radius: 20px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.55);
  cursor: pointer;
}

.histogram-panel__chart {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  align-items: end;
  gap: 6px;
  min-height: 180px;
}

.histogram-dot {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-soft);
  font-size: 0.84rem;
  font-weight: 700;
}

.histogram-dot::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.histogram-dot--red::before,
.histogram-bar--red {
  background: rgba(243, 138, 85, 0.78);
}

.histogram-dot--green::before,
.histogram-bar--green {
  background: rgba(29, 92, 85, 0.72);
}

.histogram-dot--blue::before,
.histogram-bar--blue {
  background: rgba(88, 116, 173, 0.66);
}

.histogram-dot--luma::before,
.histogram-bar--luma {
  background: rgba(16, 36, 38, 0.34);
}

.histogram-bar {
  border-radius: 999px 999px 0 0;
  opacity: 0.74;
}

.metadata-thumbnail {
  width: min(100%, 220px);
  border-radius: 22px;
  box-shadow: var(--shadow-pressed);
}

.metadata-search {
  display: grid;
  gap: 8px;
}

.metadata-search span,
.metadata-editor label span {
  color: var(--text-main);
  font-weight: 700;
}

.metadata-search input,
.metadata-editor input,
.metadata-editor textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(29, 92, 85, 0.14);
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.85);
  box-shadow: var(--shadow-pressed);
  color: var(--text-strong);
}

.metadata-search input:focus-visible,
.metadata-editor input:focus-visible,
.metadata-editor textarea:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.32);
  outline-offset: 2px;
}

.metadata-editor label {
  display: grid;
  gap: 8px;
}

.metadata-editor textarea {
  min-height: 108px;
  resize: vertical;
}

.metadata-editor__message {
  margin: 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(255, 246, 232, 0.9), rgba(229, 218, 201, 0.94));
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}

.capability-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.format-card--native {
  background:
    radial-gradient(circle at top right, rgba(255, 203, 148, 0.3), transparent 30%),
    var(--surface-muted);
}

.format-card--pipeline {
  background:
    radial-gradient(circle at top left, rgba(29, 92, 85, 0.14), transparent 28%),
    var(--surface-muted);
}

.format-card--planned {
  background:
    radial-gradient(circle at bottom right, rgba(255, 203, 148, 0.18), transparent 30%),
    var(--surface-muted);
}

.architecture-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

@media (max-width: 1180px) {
  .viewer-hero-grid {
    grid-template-columns: 1fr;
  }

  .viewer-panel {
    grid-column: span 6;
  }

  .capability-columns,
  .architecture-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .viewer-stage-card__header,
  .color-lab__hero,
  .color-lab__loupe-row,
  .audio-stage-summary,
  .audio-metadata-hero,
  .metadata-group__header,
  .format-card__meta,
  .metadata-editor__footer,
  .document-table__summary,
  .document-stage-hud {
    flex-direction: column;
    align-items: flex-start;
  }

  .viewer-panel {
    grid-column: span 12;
  }

  .audio-stage-summary,
  .audio-metadata-hero {
    grid-template-columns: 1fr;
  }

  .facts-grid,
  .metadata-group__list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .viewer-intro,
  .viewer-stage-card,
  .viewer-panel {
    padding: 20px;
  }

  .viewer-stage {
    min-height: 420px;
  }

  .viewer-image-frame,
  .viewer-document-frame,
  .viewer-video-frame,
  .viewer-audio-frame {
    min-height: 340px;
  }

  .viewer-document-frame__embed {
    min-height: 56vh;
  }

  .audio-waveform {
    grid-template-columns: repeat(auto-fit, minmax(6px, 1fr));
    gap: 4px;
    min-height: 110px;
  }

  .color-lab__swatch,
  .color-lab__loupe {
    width: 100%;
    min-width: 0;
  }
}
</style>
