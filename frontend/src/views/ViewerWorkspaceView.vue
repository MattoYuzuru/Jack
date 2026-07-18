<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppShell from '../components/ui/AppShell.vue'
import WorkspaceHeader from '../components/ui/WorkspaceHeader.vue'
import { useViewerWorkspace } from '../features/viewer/composables/useViewerWorkspace'
import { useViewerImageTools } from '../features/viewer/composables/useViewerImageTools'
import {
  useViewerVideoPlayback,
  viewerVideoSubtitleAcceptAttribute,
} from '../features/viewer/composables/useViewerVideoPlayback'
import { useViewerAudioPlayback } from '../features/viewer/composables/useViewerAudioPlayback'
import { findViewerDocumentMatches } from '../features/viewer/application/viewer-document'
import {
  buildViewerFacts,
  formatViewerPreviewLabel,
} from '../features/viewer/application/viewer-presentation'
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
import { stashEditorIncomingDraft } from '../features/editor/application/editor-handoff'
import ViewerImageRenderer from '../features/viewer/components/renderers/ViewerImageRenderer.vue'
import ViewerVideoRenderer from '../features/viewer/components/renderers/ViewerVideoRenderer.vue'
import ViewerAudioRenderer from '../features/viewer/components/renderers/ViewerAudioRenderer.vue'
import ViewerDocumentRenderer from '../features/viewer/components/renderers/ViewerDocumentRenderer.vue'
import ViewerDataRenderer from '../features/viewer/components/renderers/ViewerDataRenderer.vue'

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
const documentDraftText = ref('')
const documentDraftBaseline = ref('')
let pickerReturnFocus: HTMLElement | null = null

const router = useRouter()

const videoPlaybackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const audioPlaybackRates = [0.75, 1, 1.25, 1.5, 2]
const videoFrameRateOptions = [23.976, 24, 25, 29.97, 30, 50, 59.94, 60]

const {
  selection,
  isLoading,
  errorMessage,
  loadingMessage,
  viewerAcceptAttribute,
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

watch(
  () => (selection.value?.kind === 'document' ? selection.value.layout.editableDraft : null),
  (editableDraft) => {
    documentDraftText.value = editableDraft?.text ?? ''
    documentDraftBaseline.value = editableDraft?.text ?? ''
  },
  { immediate: true },
)

const selectionFacts = computed(() => buildViewerFacts(selection.value))

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
    `Длительность: ${formatViewerVideoDuration(selection.value.layout.durationSeconds)}`,
    `Кадр: ${selection.value.layout.width} x ${selection.value.layout.height}`,
    `Пропорции: ${selection.value.layout.metadata.aspectRatio}`,
    subtitleTracks.value.length
      ? `Субтитры: ${subtitleTracks.value.length}`
      : 'Субтитры: выключены',
  ]
})

const videoMetadataCards = computed(() => {
  if (selection.value?.kind !== 'video') {
    return []
  }

  return [
    {
      label: 'Пропорции кадра',
      value: selection.value.layout.metadata.aspectRatio,
    },
    {
      label: 'Ориентация',
      value: selection.value.layout.metadata.orientation,
    },
    {
      label: 'Оценка битрейта',
      value: formatViewerVideoBitrate(
        selection.value.layout.metadata.estimatedBitrateBitsPerSecond,
      ),
    },
    {
      label: 'Текущий кадр',
      value: approximateFrameNumber.value ? `#${approximateFrameNumber.value}` : '—',
    },
    {
      label: 'Субтитры',
      value: subtitleTracks.value.length
        ? `${subtitleTracks.value.length} дорожек / ${subtitleCueCount.value} реплик`
        : 'Не подключены',
    },
    {
      label: 'Кадры',
      value: posterCaptures.value.length ? `${posterCaptures.value.length} сохранено` : 'Пусто',
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
    `Длительность: ${formatViewerAudioDuration(selection.value.layout.durationSeconds)}`,
    `Частота: ${formatViewerSampleRate(selection.value.layout.metadata.sampleRate)}`,
    `Каналы: ${formatViewerChannelLayout(selection.value.layout.metadata.channelCount)}`,
    selection.value.artworkDataUrl ? 'Обложка: есть' : 'Обложка: нет',
  ]
})

const audioMetadataCards = computed(() => {
  if (selection.value?.kind !== 'audio') {
    return []
  }

  return [
    {
      label: 'Оценка битрейта',
      value: formatViewerAudioBitrate(
        selection.value.layout.metadata.estimatedBitrateBitsPerSecond,
      ),
    },
    {
      label: 'Частота',
      value: formatViewerSampleRate(selection.value.layout.metadata.sampleRate),
    },
    {
      label: 'Каналы',
      value: formatViewerChannelLayout(selection.value.layout.metadata.channelCount),
    },
    {
      label: 'Кодек',
      value: selection.value.layout.metadata.codec ?? '—',
    },
    {
      label: 'Контейнер',
      value: selection.value.layout.metadata.container ?? '—',
    },
    {
      label: 'Волна',
      value: selection.value.layout.waveform.length
        ? `${selection.value.layout.waveform.length} сегментов`
        : 'Недоступно',
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

const documentDatabase = computed(() => {
  if (selection.value?.kind !== 'document' || selection.value.layout.mode !== 'database') {
    return null
  }

  return selection.value.layout
})

const activeDocumentEditableDraft = computed(() =>
  selection.value?.kind === 'document' ? selection.value.layout.editableDraft : null,
)

const canQuickEditDocument = computed(() => Boolean(activeDocumentEditableDraft.value?.text))

const documentDraftFileName = computed(
  () => activeDocumentEditableDraft.value?.fileName || selection.value?.file.name || 'document.txt',
)

const documentDraftEditorFormatId = computed(
  () => activeDocumentEditableDraft.value?.editorFormatId || 'txt',
)

const documentDraftIsDirty = computed(
  () => canQuickEditDocument.value && documentDraftText.value !== documentDraftBaseline.value,
)

const documentDraftFacts = computed(() => {
  if (!canQuickEditDocument.value) {
    return []
  }

  return [
    {
      label: 'Рабочий файл',
      value: documentDraftFileName.value,
    },
    {
      label: 'Строк',
      value: String(documentDraftText.value ? documentDraftText.value.split('\n').length : 0),
    },
    {
      label: 'Символов',
      value: String(documentDraftText.value.length),
    },
    {
      label: 'Статус',
      value: documentDraftIsDirty.value ? 'Есть правки' : 'Без изменений',
    },
  ]
})

const documentModeLabel = computed(() => {
  if (selection.value?.kind !== 'document') {
    return ''
  }

  const labelMap: Record<string, string> = {
    pdf: 'PDF',
    text: 'Текст',
    table: 'Таблица',
    html: 'HTML',
    workbook: 'Листы',
    slides: 'Слайды',
    database: 'База данных',
  }

  return labelMap[selection.value.layout.mode] ?? 'Документ'
})

const documentStageMetrics = computed(() => {
  if (selection.value?.kind !== 'document') {
    return []
  }

  return selection.value.summary.slice(0, 4).map((item) => `${item.label}: ${item.value}`)
})

const documentSearchPlaceholder = computed(() => {
  if (selection.value?.kind !== 'document') {
    return 'Найти по документу...'
  }

  const placeholderMap: Record<string, string> = {
    pdf: 'договор, сумма, раздел...',
    text: 'абзац, слово, заметка...',
    table: 'колонка, клиент, значение...',
    html: 'заголовок, ссылка, абзац...',
    workbook: 'лист, ячейка, итог...',
    slides: 'заголовок, пункт, повестка...',
    database: 'таблица, поле, схема, значение...',
  }

  return placeholderMap[selection.value.layout.mode] ?? 'Найти по документу...'
})

const activeDocumentMatch = computed(
  () => documentMatches.value[activeDocumentMatchIndex.value] ?? null,
)

function openFilePicker() {
  pickerReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  fileInput.value?.click()
}

function handleStageClick() {
  if (!selection.value) {
    openFilePicker()
  }
}

function openSubtitlePicker() {
  pickerReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  subtitleInput.value?.click()
}

function restorePickerFocus() {
  const target = pickerReturnFocus
  pickerReturnFocus = null
  window.setTimeout(() => target?.focus(), 0)
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]

  if (!file) {
    restorePickerFocus()
    return
  }

  void selectFile(file)
  target.value = ''
  restorePickerFocus()
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

  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      return
    }

    await previewStage.value.requestFullscreen?.()
  } catch {
    documentActionMessage.value = 'Браузер не разрешил переключить полноэкранный режим.'
  }
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
        : 'JSON-файл с изменениями метаданных собран и скачан.'
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

  try {
    await navigator.clipboard.writeText(selection.value.searchableText)
    documentActionMessage.value = 'Извлечённый text layer скопирован в clipboard.'
  } catch {
    documentActionMessage.value = 'Браузер не разрешил запись текста в clipboard.'
  }
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
    new Blob([selection.value.searchableText], {
      type: 'text/plain;charset=utf-8',
    }),
    `${baseName}.jack-extracted.txt`,
  )
  documentActionMessage.value = 'Извлечённый text layer собран в отдельный `.txt` файл.'
}

async function copyDocumentDraft() {
  if (!canQuickEditDocument.value) {
    return
  }

  if (!navigator.clipboard) {
    documentActionMessage.value = 'Clipboard API недоступен в текущем окружении.'
    return
  }

  try {
    await navigator.clipboard.writeText(documentDraftText.value)
    documentActionMessage.value = 'Рабочая копия документа скопирована в буфер.'
  } catch {
    documentActionMessage.value = 'Браузер не разрешил запись рабочей копии в clipboard.'
  }
}

function downloadDocumentDraft() {
  if (!canQuickEditDocument.value) {
    return
  }

  downloadBlob(
    new Blob([documentDraftText.value], { type: 'text/plain;charset=utf-8' }),
    documentDraftFileName.value,
  )
  documentActionMessage.value = `Рабочая копия сохранена как ${documentDraftFileName.value}.`
}

function resetDocumentDraft() {
  if (!canQuickEditDocument.value) {
    return
  }

  documentDraftText.value = documentDraftBaseline.value
  documentActionMessage.value = 'Рабочая копия возвращена к исходному содержимому.'
}

async function openDocumentDraftInEditor() {
  if (!canQuickEditDocument.value) {
    return
  }

  const handoff = stashEditorIncomingDraft({
    formatId: documentDraftEditorFormatId.value,
    fileName: documentDraftFileName.value,
    content: documentDraftText.value,
    sourceLabel: selection.value?.file.name || 'viewer',
  })

  if (!handoff.accepted) {
    documentActionMessage.value = handoff.message
    return
  }

  await router.push('/editor')
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

async function onSubtitleChange(event: Event) {
  const target = event.target as HTMLInputElement
  const files = target.files

  if (!files?.length) {
    restorePickerFocus()
    return
  }

  await loadSubtitleFiles(files)
  target.value = ''
  restorePickerFocus()
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
  <AppShell class="viewer-workspace">
    <WorkspaceHeader eyebrow="Jack · Viewer" title="Просмотр файлов">
      <template #actions>
        <RouterLink class="back-link" to="/">На главную</RouterLink>
        <span class="chip-pill">Изображения, документы, медиа</span>
        <span class="chip-pill chip-pill--accent">Видео и аудио в одном экране</span>
      </template>
    </WorkspaceHeader>

    <section class="viewer-hero-grid">
      <article class="panel-surface viewer-intro">
        <p class="eyebrow">Viewer</p>
        <h1>Открой файл и сразу смотри содержимое.</h1>
        <p class="lead">
          Документы, таблицы, изображения, видео и аудио открываются в одном окне. Поиск, быстрые
          действия и переход в Editor появляются по типу файла.
        </p>

        <input
          ref="fileInput"
          class="visually-hidden"
          type="file"
          aria-label="Выбрать файл для просмотра"
          :accept="viewerAcceptAttribute"
          @change="onFileChange"
        />
        <input
          ref="subtitleInput"
          class="visually-hidden"
          type="file"
          aria-label="Добавить субтитры к видео"
          :accept="viewerVideoSubtitleAcceptAttribute"
          multiple
          @change="onSubtitleChange"
        />

        <div class="viewer-quick-actions">
          <button class="action-button action-button--accent" type="button" @click="openFilePicker">
            Выбрать файл
          </button>
          <button
            class="action-button"
            type="button"
            :disabled="!selection"
            @click="clearSelection"
          >
            Очистить
          </button>
        </div>

        <div class="signal-row">
          <span class="chip-pill">Документы</span>
          <span class="chip-pill">Таблицы</span>
          <span class="chip-pill">Изображения</span>
          <span class="chip-pill">Видео</span>
          <span class="chip-pill">Аудио</span>
          <span class="chip-pill">Поиск и рабочая копия</span>
        </div>
      </article>

      <article class="panel-surface viewer-stage-card">
        <div class="viewer-stage-card__header">
          <div>
            <p class="eyebrow">Просмотр</p>
            <h2>{{ selection?.file.name ?? 'Перетащи файл в окно' }}</h2>
          </div>

          <div class="viewer-toolbar">
            <template v-if="selection?.kind === 'image'">
              <button class="icon-button" type="button" aria-label="Уменьшить" @click="zoomOut">
                -
              </button>
              <button class="icon-button" type="button" aria-label="Увеличить" @click="zoomIn">
                +
              </button>
              <button class="icon-button" type="button" @click="rotateLeft">-90°</button>
              <button class="icon-button" type="button" @click="rotateRight">+90°</button>
              <button class="icon-button" type="button" @click="resetViewportTransform">
                Сброс
              </button>
              <button class="icon-button" type="button" @click="toggleTransparencyGrid">
                {{ isTransparencyGridVisible ? 'Без сетки' : 'Сетка' }}
              </button>
            </template>
            <button v-if="selection" class="icon-button" type="button" @click="toggleFullscreen">
              {{ isFullscreen ? 'Окно' : 'Экран' }}
            </button>
          </div>
        </div>

        <div
          ref="previewStage"
          class="viewer-stage"
          :class="{
            'viewer-stage--active': isDragActive,
            'viewer-stage--checker': selection?.kind === 'image' && isTransparencyGridVisible,
            'viewer-stage--interactive': !selection,
          }"
          @click="handleStageClick"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <div v-if="isLoading" class="viewer-empty-state">
            <strong>Подготавливаю просмотр...</strong>
            <span>{{ loadingMessage }}</span>
          </div>

          <div v-else-if="errorMessage" class="viewer-empty-state viewer-empty-state--warning">
            <strong>Не удалось подготовить просмотр</strong>
            <span>{{ errorMessage }}</span>
          </div>

          <ViewerImageRenderer
            v-else-if="selection?.kind === 'image'"
            :selection="selection"
            :viewport-transform="viewportTransform"
            @element-change="previewImage = $event"
            @pointer-move="handlePointerMove"
            @pointer-leave="handlePointerLeave"
            @store-swatch="storeActiveSwatch"
          />

          <ViewerVideoRenderer
            v-else-if="selection?.kind === 'video'"
            :selection="selection"
            :metrics="videoStageMetrics"
            :is-playing="isVideoPlaying"
            :is-muted="isVideoMuted"
            :volume="videoVolume"
            :playback-rate="videoPlaybackRate"
            :playback-rates="videoPlaybackRates"
            :current-time="videoCurrentTime"
            :duration-seconds="videoDurationSeconds"
            :progress-percent="videoProgressPercent"
            :current-time-label="videoCurrentTimeLabel"
            :duration-label="videoDurationLabel"
            :can-use-picture-in-picture="canUsePictureInPicture"
            :is-picture-in-picture-active="isPictureInPictureActive"
            :is-looping="isLooping"
            :assumed-frame-rate="assumedFrameRate"
            :frame-rate-options="videoFrameRateOptions"
            :frame-step-label="frameStepLabel"
            :approximate-frame-number="approximateFrameNumber"
            :subtitle-tracks="subtitleTracks"
            :active-subtitle-track="activeSubtitleTrack"
            :active-subtitle-track-id="activeSubtitleTrackId"
            :playback-message="playbackMessage"
            :subtitle-message="subtitleMessage"
            :poster-message="posterMessage"
            :poster-count="posterCaptures.length"
            @element-change="previewVideo = $event"
            @toggle-playback="togglePlayback"
            @step-frame="stepFrame"
            @seek-by="seekBy"
            @seek-to="seekTo"
            @set-volume="setVolume"
            @toggle-mute="toggleMute"
            @set-playback-rate="setPlaybackRate"
            @set-frame-rate="setAssumedFrameRate"
            @toggle-loop="toggleLoop"
            @open-subtitles="openSubtitlePicker"
            @capture-poster="capturePoster"
            @clear-subtitles="clearSubtitleTracks"
            @toggle-picture-in-picture="togglePictureInPicture"
            @copy-timestamp="copyCurrentTimestamp"
          />
          <ViewerAudioRenderer
            v-else-if="selection?.kind === 'audio'"
            :selection="selection"
            :metrics="audioStageMetrics"
            :is-playing="isAudioPlaying"
            :is-muted="isAudioMuted"
            :volume="audioVolume"
            :playback-rate="audioPlaybackRate"
            :playback-rates="audioPlaybackRates"
            :current-time="audioCurrentTime"
            :duration-seconds="audioDurationSeconds"
            :progress-percent="audioProgressPercent"
            :current-time-label="audioCurrentTimeLabel"
            :duration-label="audioDurationLabel"
            :is-looping="isAudioLooping"
            :playback-message="audioPlaybackMessage"
            @element-change="previewAudio = $event"
            @toggle-playback="toggleAudioPlayback"
            @seek-by="seekAudioBy"
            @seek-to="seekAudioTo"
            @set-volume="setAudioVolume"
            @toggle-mute="toggleAudioMute"
            @set-playback-rate="setAudioPlaybackRate"
            @toggle-loop="toggleAudioLoop"
            @copy-timestamp="copyAudioTimestamp"
          />
          <ViewerDataRenderer
            v-else-if="
              selection?.kind === 'document' &&
              ['table', 'workbook', 'database'].includes(selection.layout.mode)
            "
            :selection="selection"
            :sheet-index="documentSheetIndex"
            :database-table-index="documentDatabaseTableIndex"
            @select-sheet="selectDocumentSheet"
            @select-database-table="selectDocumentDatabaseTable"
          />

          <ViewerDocumentRenderer
            v-else-if="selection?.kind === 'document'"
            :selection="selection"
            :mode-label="documentModeLabel"
            :metrics="documentStageMetrics"
            :action-message="documentActionMessage"
            :can-quick-edit="canQuickEditDocument"
            :search-query="documentQuery"
            :slide-index="documentSlideIndex"
            @copy-text="copyDocumentText"
            @download-text="downloadDocumentText"
            @open-editor="openDocumentDraftInEditor"
            @clear-search="clearDocumentSearch"
            @select-slide="selectDocumentSlide"
          />

          <div
            v-else-if="selection?.kind === 'unknown'"
            class="viewer-empty-state viewer-empty-state--warning"
          >
            <strong>{{ selection.headline }}</strong>
            <span>{{ selection.detail }}</span>
            <p>{{ selection.nextStep }}</p>
          </div>

          <div v-else class="viewer-empty-state">
            <strong>{{
              isDragActive ? 'Отпусти файл, чтобы открыть его' : 'Перетащи файл сюда'
            }}</strong>
            <span>Или нажми на окно, чтобы выбрать документ, изображение, видео или аудио.</span>
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
            {{ formatViewerPreviewLabel(selection) }}
          </span>
        </div>
      </article>
    </section>

    <section v-if="selection?.kind === 'image'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Color Lab</p>
        <h2>Цвет, пипетка и палитра</h2>

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
            <img
              class="color-lab__loupe"
              :src="activeSample.loupeDataUrl"
              alt="Увеличенный фрагмент"
            />
            <div class="color-lab__actions">
              <button class="action-button" type="button" @click="copyActiveSample('hex')">
                HEX
              </button>
              <button class="action-button" type="button" @click="copyActiveSample('rgb')">
                RGB
              </button>
              <button class="action-button" type="button" @click="copyActiveSample('hsl')">
                HSL
              </button>
              <button
                class="action-button action-button--accent"
                type="button"
                :disabled="!canUseTools"
                @click="storeActiveSwatch"
              >
                Сохранить цвет
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
        <p class="eyebrow">Сведения</p>
        <h2>Основные параметры</h2>
        <dl class="facts-grid">
          <template v-for="fact in selectionFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </template>
        </dl>
        <p v-if="!selectionFacts.length" class="viewer-panel__empty">
          После загрузки здесь появятся основные параметры файла и текущий режим просмотра.
        </p>
        <img
          v-if="metadataThumbnail"
          class="metadata-thumbnail"
          :src="metadataThumbnail"
          alt="Встроенная миниатюра"
        />
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Инспектор</p>
        <h2>EXIF, ICC и служебные поля</h2>

        <label class="metadata-search">
          <span>Фильтр по тегам</span>
          <input
            v-model="metadataQuery"
            type="text"
            placeholder="Например: ориентация, ICC, объектив"
          />
        </label>

        <div class="metadata-group-stack">
          <article v-for="group in filteredMetadataGroups" :key="group.id" class="metadata-group">
            <div class="metadata-group__header">
              <strong>{{ group.label }}</strong>
              <span class="chip-pill chip-pill--compact">{{ group.entries.length }} тегов</span>
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
        <p class="eyebrow">Редактор метаданных</p>
        <h2>Описание, автор и дата</h2>

        <form class="metadata-editor" @submit.prevent="saveMetadataDraft">
          <label>
            <span>Описание</span>
            <textarea
              v-model="metadataDraft.description"
              rows="4"
              placeholder="Краткое описание изображения"
            ></textarea>
          </label>

          <label>
            <span>Автор</span>
            <input v-model="metadataDraft.artist" type="text" placeholder="Автор или фотограф" />
          </label>

          <label>
            <span>Авторские права</span>
            <input
              v-model="metadataDraft.copyright"
              type="text"
              placeholder="Правообладатель или лицензия"
            />
          </label>

          <label>
            <span>Дата съёмки</span>
            <input v-model="metadataDraft.capturedAt" type="datetime-local" />
          </label>

          <div class="metadata-editor__footer">
            <p class="metadata-editor__mode">
              {{
                metadataEmbeddingAvailable
                  ? 'Для JPEG будет собран новый файл с обновлёнными EXIF-полями.'
                  : 'Для этого формата изменения будут сохранены отдельным JSON-файлом.'
              }}
            </p>
            <button
              class="action-button action-button--accent"
              type="submit"
              :disabled="isSavingMetadata"
            >
              {{ isSavingMetadata ? 'Готовлю файл...' : 'Сохранить изменения' }}
            </button>
          </div>
        </form>

        <p v-if="metadataSaveMessage" class="metadata-editor__message">
          {{ metadataSaveMessage }}
        </p>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'video'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Видео</p>
        <h2>Основные параметры</h2>
        <div class="document-summary-row">
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.format.label
          }}</span>
          <span class="chip-pill chip-pill--compact"
            >{{ videoCurrentTimeLabel }} / {{ videoDurationLabel }}</span
          >
          <span class="chip-pill chip-pill--compact">{{ isVideoPlaying ? 'Идёт' : 'Пауза' }}</span>
          <span class="chip-pill chip-pill--compact">{{
            isLooping ? 'Повтор включён' : 'Повтор выключен'
          }}</span>
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
        <p class="eyebrow">Точная навигация</p>
        <h2>Кадры, скорость и ориентиры</h2>
        <div class="outline-stack">
          <article v-for="card in videoMetadataCards" :key="card.label" class="outline-card">
            <strong>{{ card.label }}</strong>
            <span>{{ card.value }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Субтитры</p>
        <h2>Подключай и переключай дорожки</h2>
        <div class="viewer-dropzone__actions">
          <button
            class="action-button action-button--accent"
            type="button"
            @click="openSubtitlePicker"
          >
            Добавить файл субтитров
          </button>
          <button
            class="action-button"
            type="button"
            :class="{
              'action-button--accent': activeSubtitleTrackId === 'off',
            }"
            @click="setActiveSubtitleTrack('off')"
          >
            Выключить
          </button>
          <button
            class="action-button"
            type="button"
            :disabled="!subtitleTracks.length"
            @click="clearSubtitleTracks"
          >
            Очистить всё
          </button>
        </div>
        <div v-if="subtitleTracks.length" class="subtitle-track-grid">
          <article
            v-for="track in subtitleTracks"
            :key="track.id"
            class="subtitle-track-card"
            :class="{
              'subtitle-track-card--active': activeSubtitleTrackId === track.id,
            }"
          >
            <button
              class="subtitle-track-card__select"
              type="button"
              @click="setActiveSubtitleTrack(track.id)"
            >
              <strong>{{ track.label }}</strong>
              <span
                >{{ track.language.toUpperCase() }} · {{ track.cueCount }} реплик ·
                {{ track.format.toUpperCase() }}</span
              >
            </button>
            <button
              class="subtitle-track-card__action"
              type="button"
              @click="removeSubtitleTrack(track.id)"
            >
              Удалить
            </button>
          </article>
        </div>
        <p v-else class="viewer-panel__empty">
          Можно временно подключить `.vtt` или `.srt`, чтобы посмотреть видео с субтитрами без
          изменения исходного файла.
        </p>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Сохранённые кадры</p>
        <h2>Сохраняй удачные кадры</h2>
        <div class="viewer-dropzone__actions">
          <button class="action-button action-button--accent" type="button" @click="capturePoster">
            Сохранить текущий кадр
          </button>
          <button class="action-button" type="button" @click="copyCurrentTimestamp">
            Скопировать время
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
                Перейти
              </button>
              <button
                class="action-button"
                type="button"
                @click="downloadPosterCapture(capture.id)"
              >
                Скачать
              </button>
              <button class="action-button" type="button" @click="removePosterCapture(capture.id)">
                Удалить
              </button>
            </div>
          </article>
        </div>
        <p v-else class="viewer-panel__empty">
          Здесь будут собираться кадры, которые стоит сохранить как обложку, иллюстрацию или
          промежуточный референс.
        </p>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'audio'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Аудио</p>
        <h2>Параметры дорожки</h2>
        <div class="document-summary-row">
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.format.label
          }}</span>
          <span class="chip-pill chip-pill--compact"
            >{{ audioCurrentTimeLabel }} / {{ audioDurationLabel }}</span
          >
          <span class="chip-pill chip-pill--compact">{{ isAudioPlaying ? 'Идёт' : 'Пауза' }}</span>
          <span class="chip-pill chip-pill--compact">{{
            isAudioLooping ? 'Повтор включён' : 'Повтор выключен'
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
        <p class="eyebrow">Воспроизведение</p>
        <h2>Волна, скорость и звучание</h2>
        <div class="outline-stack">
          <article v-for="card in audioMetadataCards" :key="card.label" class="outline-card">
            <strong>{{ card.label }}</strong>
            <span>{{ card.value }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Теги и обложка</p>
        <h2>Теги и карточка трека</h2>

        <div class="audio-metadata-hero">
          <div v-if="selection.artworkDataUrl" class="audio-metadata-hero__artwork">
            <img :src="selection.artworkDataUrl" :alt="`${selection.file.name} artwork`" />
          </div>
          <div v-else class="audio-metadata-hero__artwork audio-metadata-hero__artwork--empty">
            <strong>{{ selection.format.label }}</strong>
            <span>Нет встроенной обложки</span>
          </div>

          <div class="audio-metadata-hero__copy">
            <span class="chip-pill chip-pill--compact chip-pill--accent">
              {{ filteredAudioMetadataGroups.length }} групп
            </span>
            <p>Здесь собраны основные теги, технические параметры и дополнительные поля дорожки.</p>
          </div>
        </div>

        <label class="metadata-search">
          <span>Фильтр по тегам</span>
          <input
            v-model="audioMetadataQuery"
            type="text"
            placeholder="Например: артист, альбом, кодек"
          />
        </label>

        <div class="metadata-group-stack">
          <article
            v-for="group in filteredAudioMetadataGroups"
            :key="group.id"
            class="metadata-group"
          >
            <div class="metadata-group__header">
              <strong>{{ group.label }}</strong>
              <span class="chip-pill chip-pill--compact">{{ group.entries.length }} тегов</span>
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
    </section>

    <section v-else-if="selection?.kind === 'document'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Документ</p>
        <h2>Ключевые параметры</h2>
        <div class="document-summary-row">
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.format.label
          }}</span>
          <span class="chip-pill chip-pill--compact">{{ documentModeLabel }}</span>
          <span class="chip-pill chip-pill--compact">Совпадений: {{ documentMatches.length }}</span>
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
        <p class="eyebrow">Быстрый поиск</p>
        <h2>Найди нужный фрагмент</h2>
        <label class="metadata-search">
          <span>Поиск</span>
          <input v-model="documentQuery" type="text" :placeholder="documentSearchPlaceholder" />
        </label>
        <div class="document-search-toolbar">
          <span class="chip-pill chip-pill--compact">
            {{ documentQuery ? `${documentMatches.length} совпадений` : 'Поиск готов' }}
          </span>
          <span v-if="activeDocumentMatch" class="chip-pill chip-pill--compact chip-pill--accent">
            Фрагмент {{ activeDocumentMatchIndex + 1 }}
          </span>
        </div>
        <div v-if="documentMatches.length" class="search-match-stack">
          <button
            v-for="(match, matchIndex) in documentMatches"
            :key="match.id"
            class="search-match-card"
            :class="{
              'search-match-card--active': activeDocumentMatchIndex === matchIndex,
            }"
            type="button"
            @click="focusDocumentMatch(matchIndex)"
          >
            <strong>Результат {{ matchIndex + 1 }}</strong>
            <span>{{ match.excerpt }}</span>
          </button>
        </div>
        <p v-else class="viewer-panel__empty">
          {{
            documentQuery
              ? 'Совпадения не найдены.'
              : 'Поиск работает по тексту, который Jack смог извлечь из документа.'
          }}
        </p>
      </article>

      <article v-if="canQuickEditDocument" class="panel-surface viewer-panel">
        <p class="eyebrow">Рабочая копия</p>
        <h2>Текст для правок и передачи в Editor</h2>
        <p class="viewer-panel__copy">
          Здесь редактируется безопасная копия содержимого. Исходный файл не меняется.
        </p>

        <div class="fact-grid">
          <article v-for="fact in documentDraftFacts" :key="fact.label" class="fact-chip">
            <span>{{ fact.label }}</span>
            <strong>{{ fact.value }}</strong>
          </article>
        </div>

        <label class="metadata-search metadata-search--stacked">
          <span>Рабочая копия</span>
          <textarea
            v-model="documentDraftText"
            class="document-draft-textarea"
            rows="12"
            spellcheck="false"
          />
        </label>

        <div class="document-draft-actions">
          <button
            class="action-button"
            type="button"
            :disabled="!documentDraftIsDirty"
            @click="resetDocumentDraft"
          >
            Сбросить
          </button>
          <button class="action-button" type="button" @click="copyDocumentDraft">
            Скопировать
          </button>
          <button class="action-button" type="button" @click="downloadDocumentDraft">
            Скачать
          </button>
          <button
            class="action-button action-button--accent"
            type="button"
            @click="openDocumentDraftInEditor"
          >
            Продолжить в Editor
          </button>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Структура</p>
        <h2>Заголовки, листы и части документа</h2>

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
            <strong>Колонка</strong>
            <span>{{ column }}</span>
          </article>
        </div>

        <div v-else-if="documentWorkbook" class="outline-stack">
          <article
            v-for="(sheet, sheetIndex) in documentWorkbook.sheets"
            :key="sheet.id"
            class="outline-card outline-card--interactive"
            :class="{
              'outline-card--active': documentSheetIndex === sheetIndex,
            }"
            @click="selectDocumentSheet(sheetIndex)"
          >
            <strong>Лист</strong>
            <span>{{ sheet.name }} · {{ sheet.table.totalRows }} строк</span>
          </article>
        </div>

        <div v-else-if="documentSlides.length" class="outline-stack">
          <article
            v-for="(slide, slideIndex) in documentSlides"
            :key="slide.id"
            class="outline-card outline-card--interactive"
            :class="{
              'outline-card--active': documentSlideIndex === slideIndex,
            }"
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
            :class="{
              'outline-card--active': documentDatabaseTableIndex === tableIndex,
            }"
            @click="selectDocumentDatabaseTable(tableIndex)"
          >
            <strong>Таблица</strong>
            <span>{{
              table.rowCount == null ? table.name : `${table.name} · ${table.rowCount} строк`
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
          Для этого типа документа доступны только основные сведения и поиск по тексту.
        </p>
      </article>
    </section>
  </AppShell>
</template>

<style scoped>
.viewer-workspace {
  display: grid;
  gap: 22px;
}

.viewer-hero-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.74fr) minmax(0, 1.26fr);
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
  margin: 18px 0 0;
  color: var(--text-soft);
  font-size: 1rem;
}

.viewer-quick-actions,
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

.viewer-quick-actions {
  margin-top: 22px;
}

.signal-row {
  margin-top: 18px;
}

.viewer-panel__empty,
.format-card p,
.architecture-card p,
.metadata-editor__mode {
  color: var(--text-soft);
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

.viewer-toolbar .icon-button {
  min-width: 0;
  padding-inline: 12px;
}

.viewer-stage {
  display: grid;
  min-height: 520px;
  place-items: center;
  padding: 20px;
  border-radius: calc(var(--radius-2xl) - 8px);
  border: 1px solid transparent;
  background:
    radial-gradient(circle at top right, rgba(255, 196, 129, 0.18), transparent 26%),
    linear-gradient(155deg, rgba(255, 251, 245, 0.8), rgba(227, 216, 201, 0.86));
  box-shadow: var(--shadow-pressed);
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.viewer-stage--interactive {
  cursor: pointer;
  border-color: rgba(29, 92, 85, 0.2);
  border-style: dashed;
}

.viewer-stage--active {
  transform: translateY(-2px);
  border-color: rgba(29, 92, 85, 0.46);
  box-shadow: var(--shadow-floating);
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
.metadata-search textarea,
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
.metadata-search textarea:focus-visible,
.metadata-editor input:focus-visible,
.metadata-editor textarea:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.32);
  outline-offset: 2px;
}

.metadata-search--stacked {
  align-content: start;
}

.document-draft-textarea {
  min-height: 220px;
  resize: vertical;
  font-family: var(--font-mono);
  line-height: 1.55;
}

.document-draft-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.viewer-panel__copy {
  margin: 0;
  color: var(--text-main);
  line-height: 1.65;
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
