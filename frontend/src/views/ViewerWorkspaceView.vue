<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
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
import { stashEditorIncomingDraft } from '../features/editor/application/editor-handoff'
import type { ViewerFormatDefinition } from '../features/viewer/domain/viewer-registry'

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

const router = useRouter()

const videoPlaybackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const audioPlaybackRates = [0.75, 1, 1.25, 1.5, 2]
const videoFrameRateOptions = [23.976, 24, 25, 29.97, 30, 50, 59.94, 60]
const videoShortcutHints = [
  { keys: 'Space', description: 'Пауза / воспроизведение' },
  { keys: '← / →', description: 'Шаг -5с / +5с' },
  { keys: 'Shift + ← / →', description: 'Один кадр назад / вперёд' },
  { keys: 'M', description: 'Вкл / выкл звук' },
  { keys: 'L', description: 'Вкл / выкл повтор' },
  { keys: 'P', description: 'Картинка в картинке' },
  { keys: 'C', description: 'Скопировать текущее время' },
]
const audioShortcutHints = [
  { keys: 'Space', description: 'Пауза / воспроизведение' },
  { keys: '← / →', description: 'Шаг -10с / +10с' },
  { keys: 'M', description: 'Вкл / выкл звук' },
  { keys: 'L', description: 'Вкл / выкл повтор' },
  { keys: 'C', description: 'Скопировать текущее время' },
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

function formatViewerFormatStatus(format: ViewerFormatDefinition): string {
  if (!format.available) {
    return 'Временно недоступно'
  }

  if (format.previewPipeline === 'planned') {
    return 'Скоро в Viewer'
  }

  if (format.previewPipeline === 'browser-native') {
    return 'Открывается сразу'
  }

  return format.family === 'document' ? 'Расширенный просмотр' : 'Подготовленный просмотр'
}

function formatViewerFormatNote(format: ViewerFormatDefinition): string {
  switch (format.extension) {
    case 'jpg':
    case 'jpeg':
      return 'Подходит для фотографий и быстро открывается вместе с основными метаданными.'
    case 'png':
      return 'Удобен для макетов, скриншотов и изображений с прозрачностью.'
    case 'webp':
      return 'Хорошо подходит для веб-графики и открывается без дополнительной подготовки.'
    case 'avif':
      return 'Современный компактный формат для изображений с высоким качеством.'
    case 'gif':
      return 'Сохраняет анимацию и запускается сразу после загрузки.'
    case 'bmp':
      return 'Полезен для технических исходников и файлов без сжатия.'
    case 'svg':
      return 'Вектор открывается чисто и без потери резкости при масштабировании.'
    case 'ico':
      return 'Можно быстро проверить набор иконок и их визуальное качество.'
    case 'heic':
      return 'Подходит для фото с iPhone и новых камер: Jack готовит просмотр и метаданные.'
    case 'tiff':
      return 'Удобен для сканов и печатных материалов: Jack собирает просмотр и основные сведения.'
    case 'raw':
      return 'Подходит для исходников с камеры: Jack извлекает превью и параметры съёмки.'
    case 'pdf':
      return 'Открывается постранично, с поиском по тексту и быстрым переходом по документу.'
    case 'txt':
      return 'Показывает текст без лишнего оформления и позволяет быстро искать нужные фрагменты.'
    case 'md':
      return 'Показывает структуру документа, заголовки и удобный текст для чтения.'
    case 'json':
      return 'Помогает быстро проверить структуру данных и рабочую копию для правок.'
    case 'yaml':
      return 'Подходит для конфигов: видно секции, структуру и удобную рабочую копию.'
    case 'xml':
      return 'Открывает XML как читаемый документ с сохранением структуры.'
    case 'env':
      return 'Показывает переменные среды в удобном виде и помогает быстро поправить значения.'
    case 'csv':
    case 'tsv':
      return 'Открывает табличные данные по строкам и колонкам без лишней подготовки.'
    case 'html':
      return 'Показывает очищенный HTML с удобным просмотром структуры и текста.'
    case 'log':
      return 'Подходит для длинных логов с поиском по содержимому.'
    case 'sql':
      return 'Удобно для чтения SQL-скриптов, поиска и быстрой рабочей копии.'
    case 'rtf':
    case 'doc':
    case 'docx':
    case 'odt':
      return 'Показывает текст документа, структуру и удобный режим чтения.'
    case 'xls':
    case 'xlsx':
      return 'Открывает листы и помогает быстро проверить таблицы и значения.'
    case 'pptx':
      return 'Показывает слайды и текстовую структуру презентации.'
    case 'epub':
      return 'Подходит для чтения книги по главам и поиска по содержимому.'
    case 'db':
    case 'sqlite':
      return 'Показывает таблицы, поля и примеры строк без отдельного клиента базы данных.'
    case 'mp4':
    case 'mov':
    case 'webm':
      return 'Видео запускается сразу с навигацией по времени, кадрам и субтитрам.'
    case 'avi':
    case 'mkv':
    case 'wmv':
    case 'flv':
      return 'Jack подготавливает стабильное воспроизведение даже для тяжёлых контейнеров.'
    case 'mp3':
    case 'wav':
    case 'ogg':
    case 'opus':
      return 'Сразу доступны воспроизведение, длительность, волна и основные теги.'
    case 'aac':
    case 'flac':
    case 'aiff':
      return 'Jack готовит волну, обложку и теги для компактных и lossless-дорожек.'
    default:
      return format.notes
  }
}

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

watch(
  () => (selection.value?.kind === 'document' ? selection.value.layout.editableDraft : null),
  (editableDraft) => {
    documentDraftText.value = editableDraft?.text ?? ''
    documentDraftBaseline.value = editableDraft?.text ?? ''
  },
  { immediate: true },
)

function formatSelectionPreviewLabel(): string {
  if (!selection.value) {
    return ''
  }

  if (selection.value.kind === 'image') {
    return selection.value.format.previewPipeline === 'browser-native'
      ? 'Мгновенный просмотр'
      : 'Подготовленный просмотр'
  }

  if (selection.value.kind === 'video') {
    return 'Видеоплеер'
  }

  if (selection.value.kind === 'audio') {
    return 'Аудиоплеер'
  }

  if (selection.value.kind !== 'document') {
    return 'Просмотр'
  }

  switch (selection.value.layout.mode) {
    case 'pdf':
      return 'Постраничный просмотр'
    case 'table':
      return 'Табличный просмотр'
    case 'html':
      return 'Веб-предпросмотр'
    case 'workbook':
      return 'Рабочая книга'
    case 'slides':
      return 'Просмотр слайдов'
    case 'database':
      return 'Структура базы'
    default:
      return 'Текстовый просмотр'
  }
}

function formatViewerFactLabel(label: string): string {
  switch (label) {
    case 'Headings':
      return 'Заголовки'
    case 'Delimiter':
      return 'Разделитель'
    case 'Sandbox':
      return 'Режим HTML'
    case 'Top-level keys':
      return 'Ключи верхнего уровня'
    case 'Root node':
      return 'Корневой узел'
    case 'Outline entries':
      return 'Элементы структуры'
    case 'Sheets':
      return 'Листы'
    case 'Rows':
      return 'Строки'
    case 'Columns':
      return 'Колонки'
    case 'Views':
      return 'Представления'
    case 'Triggers':
      return 'Триггеры'
    case 'Sections':
      return 'Разделы'
    case 'Blocks':
      return 'Блоки'
    case 'Top-level symbols':
      return 'Символы верхнего уровня'
    case 'Root':
      return 'Корень'
    case 'Режим preview':
      return 'Режим просмотра'
    default:
      return label
  }
}

function formatViewerFactValue(_label: string, value: string): string {
  switch (value) {
    case 'Browser preview only':
      return 'Без извлечения текста'
    case 'Backend srcdoc':
      return 'Безопасный встроенный просмотр'
    case 'Backend PDF text extraction':
      return 'С поиском по тексту'
    case 'Rendered article':
      return 'Статья'
    case 'Structured config':
      return 'Структурный просмотр'
    case 'Config review':
      return 'Проверка конфигурации'
    case 'Schema read':
      return 'Просмотр структуры'
    case 'Config table':
      return 'Таблица переменных'
    case 'Delimited table preview':
      return 'Табличный просмотр'
    case 'Tabbed table preview':
      return 'Таблица с таб-разделителями'
    case 'PDF server preview':
      return 'Постраничный просмотр'
    case 'HTML sanitized preview':
      return 'Безопасный HTML'
    case 'Markdown reading preview':
      return 'Чтение Markdown'
    case 'JSON structured preview':
      return 'Структурный JSON'
    case 'YAML structured preview':
      return 'Структурный YAML'
    case 'XML structure preview':
      return 'Структура XML'
    case 'Environment config preview':
      return 'Переменные окружения'
    default:
      return value
  }
}

function normalizeViewerFacts(facts: Array<{ label: string; value: string }>) {
  return facts.map((fact) => ({
    label: formatViewerFactLabel(fact.label),
    value: formatViewerFactValue(fact.label, fact.value),
  }))
}

const selectionFacts = computed(() => {
  if (!selection.value) {
    return []
  }

  const items = [
    { label: 'Имя файла', value: selection.value.file.name },
    {
      label: 'Размер',
      value: new Intl.NumberFormat('ru-RU').format(selection.value.file.size) + ' байт',
    },
    { label: 'Расширение', value: selection.value.extension || 'неизвестно' },
    { label: 'MIME', value: selection.value.file.type || 'Не определён' },
  ]

  if (selection.value.kind === 'image') {
    items.push({
      label: 'Размерность',
      value: `${selection.value.dimensions.width} x ${selection.value.dimensions.height}`,
    })
    items.push({
      label: 'Режим просмотра',
      value: formatSelectionPreviewLabel(),
    })
    items.push(...normalizeViewerFacts(selection.value.metadata.summary))
  }

  if (selection.value.kind === 'document') {
    items.push({
      label: 'Режим просмотра',
      value: formatSelectionPreviewLabel(),
    })
    items.push(...normalizeViewerFacts(selection.value.summary))
  }

  if (selection.value.kind === 'video') {
    items.push({
      label: 'Режим просмотра',
      value: formatSelectionPreviewLabel(),
    })
    items.push(...normalizeViewerFacts(selection.value.summary))
  }

  if (selection.value.kind === 'audio') {
    items.push({
      label: 'Режим просмотра',
      value: formatSelectionPreviewLabel(),
    })
    items.push(...normalizeViewerFacts(selection.value.summary))
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

async function copyDocumentDraft() {
  if (!canQuickEditDocument.value) {
    return
  }

  if (!navigator.clipboard) {
    documentActionMessage.value = 'Clipboard API недоступен в текущем окружении.'
    return
  }

  await navigator.clipboard.writeText(documentDraftText.value)
  documentActionMessage.value = 'Рабочая копия документа скопирована в буфер.'
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

  stashEditorIncomingDraft({
    formatId: documentDraftEditorFormatId.value,
    fileName: documentDraftFileName.value,
    content: documentDraftText.value,
    sourceLabel: selection.value?.file.name || 'viewer',
  })

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
          <p class="eyebrow">Jack · Viewer</p>
          <p class="brand-lockup__title">Просмотр файлов</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <RouterLink class="back-link" to="/">На главную</RouterLink>
        <span class="chip-pill">Изображения, документы, медиа</span>
        <span class="chip-pill chip-pill--accent">Видео и аудио в одном экране</span>
      </div>
    </header>

    <section class="viewer-hero-grid">
      <article class="panel-surface viewer-intro">
        <p class="eyebrow">Открыть, просмотреть, найти нужное</p>
        <h1>
          Viewer помогает быстро разобраться с файлом: открыть документ, прослушать трек, посмотреть
          видео или изучить изображение без прыжков между разными инструментами.
        </h1>
        <p class="lead">
          Один экран закрывает основные сценарии просмотра: документы с поиском и структурой, видео
          с точной навигацией, аудио с волной и тегами, изображения с цветовым и метаданным
          разбором. Для сложных форматов Jack сам подготавливает удобное представление.
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
            <strong>Открой файл для просмотра</strong>
            <span>
              Поддерживаются изображения, документы, таблицы, презентации, PDF, видео и аудио. После
              загрузки Jack сразу покажет подходящий режим просмотра, быстрые действия и полезные
              детали именно для этого типа файла.
            </span>
          </div>

          <div class="viewer-dropzone__actions">
            <button
              class="action-button action-button--accent"
              type="button"
              @click="openFilePicker"
            >
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
        </div>

        <div class="signal-row">
          <span class="chip-pill">Изображения и метаданные</span>
          <span class="chip-pill">Документы с поиском</span>
          <span class="chip-pill">Видео с точной навигацией</span>
          <span class="chip-pill">Аудио с волной и тегами</span>
          <span class="chip-pill">Быстрый переход в editor</span>
        </div>
      </article>

      <article class="panel-surface viewer-stage-card">
        <div class="viewer-stage-card__header">
          <div>
            <p class="eyebrow">Экран просмотра</p>
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
              Влево
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="rotateRight"
            >
              Вправо
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="resetViewportTransform"
            >
              Сброс
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="!selection"
              @click="toggleFullscreen"
            >
              {{ isFullscreen ? 'Выйти из полного экрана' : 'Полный экран' }}
            </button>
            <button
              class="icon-button"
              type="button"
              :disabled="selection?.kind !== 'image'"
              @click="toggleTransparencyGrid"
            >
              {{ isTransparencyGridVisible ? 'Скрыть сетку' : 'Показать сетку' }}
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
            <strong>Подготавливаю просмотр...</strong>
            <span>{{ loadingMessage }}</span>
          </div>

          <div v-else-if="errorMessage" class="viewer-empty-state viewer-empty-state--warning">
            <strong>Не удалось подготовить просмотр</strong>
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
                  {{ isVideoPlaying ? 'Пауза' : 'Воспроизвести' }}
                </button>
                <button class="action-button" type="button" @click="stepFrame(-1)">-1f</button>
                <button class="action-button" type="button" @click="stepFrame(1)">+1f</button>
                <button class="action-button" type="button" @click="seekBy(-5)">-5s</button>
                <button class="action-button" type="button" @click="seekBy(5)">+5s</button>
                <button class="action-button" type="button" @click="toggleLoop">
                  {{ isLooping ? 'Повтор включён' : 'Повтор выключен' }}
                </button>
                <button class="action-button" type="button" @click="openSubtitlePicker">
                  Субтитры
                </button>
                <button class="action-button" type="button" @click="capturePoster">Кадр</button>
                <button
                  class="action-button"
                  type="button"
                  :disabled="!canUsePictureInPicture"
                  @click="togglePictureInPicture"
                >
                  {{ isPictureInPictureActive ? 'Выйти из PiP' : 'PiP' }}
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
                  <span>Громкость</span>
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
                  {{ isVideoMuted ? 'Со звуком' : 'Без звука' }}
                </button>
                <label class="video-rate">
                  <span>Скорость</span>
                  <select :value="videoPlaybackRate" @change="onVideoRateChange">
                    <option v-for="rate in videoPlaybackRates" :key="rate" :value="rate">
                      {{ rate }}x
                    </option>
                  </select>
                </label>
                <label class="video-rate">
                  <span>FPS</span>
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
                  {{ approximateFrameNumber ? `Кадр #${approximateFrameNumber}` : 'Кадр —' }}
                </span>
              </div>

              <div class="video-control-row">
                <div class="viewer-dropzone__actions">
                  <button class="action-button" type="button" @click="copyCurrentTimestamp">
                    Скопировать время
                  </button>
                  <button class="action-button" type="button" @click="capturePoster">
                    Сохранить кадр
                  </button>
                  <button class="action-button" type="button" @click="openSubtitlePicker">
                    Добавить субтитры
                  </button>
                  <button
                    class="action-button"
                    type="button"
                    :disabled="!subtitleTracks.length"
                    @click="clearSubtitleTracks"
                  >
                    Очистить
                  </button>
                </div>
                <div class="document-stage-hud__meta">
                  <span class="chip-pill chip-pill--compact">
                    {{ isLooping ? 'Повтор включён' : 'Один проход' }}
                  </span>
                  <span class="chip-pill chip-pill--compact">
                    {{
                      activeSubtitleTrack
                        ? `Активно: ${activeSubtitleTrack.label}`
                        : 'Субтитры выключены'
                    }}
                  </span>
                  <span class="chip-pill chip-pill--compact chip-pill--accent">
                    {{
                      posterCaptures.length
                        ? `${posterCaptures.length} кадров`
                        : 'Кадры ещё не сохранены'
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
                  {{ isAudioPlaying ? 'Пауза' : 'Воспроизвести' }}
                </button>
                <button class="action-button" type="button" @click="seekAudioBy(-10)">-10s</button>
                <button class="action-button" type="button" @click="seekAudioBy(10)">+10s</button>
                <button class="action-button" type="button" @click="toggleAudioLoop">
                  {{ isAudioLooping ? 'Повтор включён' : 'Повтор выключен' }}
                </button>
                <button class="action-button" type="button" @click="copyAudioTimestamp">
                  Скопировать время
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
                  <span>Обложка не найдена</span>
                </div>

                <div class="audio-stage-copy">
                  <p class="eyebrow">Аудио</p>
                  <h3>{{ selection.file.name }}</h3>
                  <p>
                    Здесь можно быстро прослушать дорожку, проверить длительность, теги, обложку и
                    визуально оценить форму сигнала без отдельного плеера.
                  </p>
                </div>
              </div>

              <div class="audio-waveform" aria-label="Волна аудио">
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
                    <span>Громкость</span>
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
                    {{ isAudioMuted ? 'Со звуком' : 'Без звука' }}
                  </button>
                  <label class="video-rate">
                    <span>Скорость</span>
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
                    {{ isAudioLooping ? 'Повтор включён' : 'Один проход' }}
                  </span>
                  <span class="chip-pill chip-pill--compact chip-pill--accent">
                    {{ selection.layout.waveform.length ? 'Волна готова' : 'Волна недоступна' }}
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
                  Скопировать текст
                </button>
                <button class="action-button" type="button" @click="downloadDocumentText">
                  Скачать текст
                </button>
                <button
                  v-if="canQuickEditDocument"
                  class="action-button action-button--accent"
                  type="button"
                  @click="openDocumentDraftInEditor"
                >
                  Открыть в Editor
                </button>
                <button
                  class="action-button"
                  type="button"
                  :disabled="!documentQuery"
                  @click="clearDocumentSearch"
                >
                  Сбросить поиск
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
                <strong>{{ selection.layout.table.totalRows }} строк</strong>
                <span>{{ selection.layout.table.totalColumns }} колонок</span>
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
                  <strong>{{ activeDocumentSheet.table.totalRows }} строк</strong>
                  <span>{{ activeDocumentSheet.table.totalColumns }} колонок</span>
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
                      ? 'Строк: нет данных'
                      : `${activeDocumentDatabaseTable.rowCount} строк`
                  }}</strong>
                  <span>{{ activeDocumentDatabaseTable.columns.length }} колонок</span>
                </div>
                <pre>{{ activeDocumentDatabaseTable.schemaSql }}</pre>
              </article>

              <div v-if="activeDocumentDatabaseTable" class="document-table">
                <div class="document-table__summary">
                  <strong>{{ activeDocumentDatabaseTable.sample.totalRows }} строк</strong>
                  <span>{{ activeDocumentDatabaseTable.sample.totalColumns }} колонок</span>
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

              <p v-else class="viewer-panel__empty">В этой базе не найдено таблиц для просмотра.</p>
            </div>

            <div v-else-if="selection.layout.mode === 'slides'" class="document-slide-grid">
              <article
                v-if="activeDocumentSlide"
                class="document-slide-card document-slide-card--focus"
              >
                <div class="document-slide-card__meta">
                  <span class="chip-pill chip-pill--compact chip-pill--accent">
                    Активный слайд {{ documentSlideIndex + 1 }}
                  </span>
                </div>
                <h3>{{ activeDocumentSlide.title }}</h3>
                <ul v-if="activeDocumentSlide.bullets.length" class="document-slide-card__list">
                  <li v-for="bullet in activeDocumentSlide.bullets" :key="bullet">{{ bullet }}</li>
                </ul>
                <p v-else class="viewer-panel__empty">На выбранном слайде нет пунктов списка.</p>
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
                  <span class="chip-pill chip-pill--compact">Слайд {{ slideIndex + 1 }}</span>
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
            <strong>Экран готов к работе</strong>
            <span>
              Перетащи файл или открой его через кнопку выше, чтобы сразу увидеть подходящий режим
              просмотра, поиск, метаданные и быстрые действия.
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
            {{ formatSelectionPreviewLabel() }}
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
        <h2>Основная информация о файле</h2>
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
        <h2>EXIF, ICC и другие группы метаданных</h2>

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
        <h2>Подправь описание, автора и дату съёмки</h2>

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

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Поддержка форматов</p>
        <h2>Что можно открыть в режиме просмотра изображений</h2>
        <div class="capability-columns">
          <div class="format-grid">
            <article
              v-for="format in browserNativeFormats"
              :key="format.extension"
              class="format-card format-card--native"
            >
              <div class="format-card__meta">
                <strong>{{ format.label }}</strong>
                <span class="chip-pill chip-pill--compact">{{
                  formatViewerFormatStatus(format)
                }}</span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
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
                  {{ formatViewerFormatStatus(format) }}
                </span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
            </article>
          </div>
        </div>
        <p v-if="!plannedMediaFormats.length" class="viewer-panel__empty">
          Все заявленные графические форматы уже открываются в этом экране без отдельных обходных
          сценариев.
        </p>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'video'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Видео</p>
        <h2>Основные параметры и предупреждения</h2>
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
        <h2>Кадры, скорость и быстрые ориентиры</h2>
        <div class="outline-stack">
          <article v-for="card in videoMetadataCards" :key="card.label" class="outline-card">
            <strong>{{ card.label }}</strong>
            <span>{{ card.value }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Субтитры</p>
        <h2>Подключай дорожки и быстро переключайся между ними</h2>
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
            :class="{ 'action-button--accent': activeSubtitleTrackId === 'off' }"
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
            :class="{ 'subtitle-track-card--active': activeSubtitleTrackId === track.id }"
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
        <h2>Выбирай удачные моменты и сохраняй их как отдельные изображения</h2>
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

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Горячие клавиши</p>
        <h2>Быстрая навигация по видео с клавиатуры</h2>
        <div class="outline-stack">
          <article v-for="shortcut in videoShortcutHints" :key="shortcut.keys" class="outline-card">
            <strong>{{ shortcut.keys }}</strong>
            <span>{{ shortcut.description }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Поддержка форматов</p>
        <h2>Какие видео открываются в этом режиме</h2>
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
                  {{ formatViewerFormatStatus(format) }}
                </span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
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
                <span class="chip-pill chip-pill--compact">{{
                  formatViewerFormatStatus(format)
                }}</span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
            </article>
          </div>
        </div>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'audio'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Аудио</p>
        <h2>Параметры дорожки и важные замечания</h2>
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
        <h2>Волна сигнала, скорость и параметры звучания</h2>
        <div class="outline-stack">
          <article v-for="card in audioMetadataCards" :key="card.label" class="outline-card">
            <strong>{{ card.label }}</strong>
            <span>{{ card.value }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Теги и обложка</p>
        <h2>Быстро найди нужные поля и проверь карточку трека</h2>

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

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Горячие клавиши</p>
        <h2>Управление воспроизведением с клавиатуры</h2>
        <div class="outline-stack">
          <article v-for="shortcut in audioShortcutHints" :key="shortcut.keys" class="outline-card">
            <strong>{{ shortcut.keys }}</strong>
            <span>{{ shortcut.description }}</span>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Поддержка форматов</p>
        <h2>Какие аудиофайлы открываются в этом режиме</h2>
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
                  {{ formatViewerFormatStatus(format) }}
                </span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
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
                <span class="chip-pill chip-pill--compact">{{
                  formatViewerFormatStatus(format)
                }}</span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
            </article>
          </div>
        </div>
        <p v-if="!plannedAudioFormats.length" class="viewer-panel__empty">
          Все заявленные аудиоформаты уже открываются в этом экране без отдельного обходного
          сценария.
        </p>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'document'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Документ</p>
        <h2>Ключевые параметры и замечания</h2>
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
        <h2>Найди нужный фрагмент по всему доступному тексту документа</h2>
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
            :class="{ 'search-match-card--active': activeDocumentMatchIndex === matchIndex }"
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
        <h2>Рабочая копия для правок, заметок и передачи в editor</h2>
        <p class="viewer-panel__copy">
          Здесь редактируется безопасная копия содержимого. Исходный файл не меняется, поэтому можно
          быстро подчистить текст, собрать выдержку или отправить материал в editor для
          форматирования и экспорта.
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
        <h2>Заголовки, листы, таблицы и другие части документа</h2>

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
            :class="{ 'outline-card--active': documentSheetIndex === sheetIndex }"
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

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Поддержка форматов</p>
        <h2>Какие документы уже открываются в этом режиме</h2>
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
                  {{ formatViewerFormatStatus(format) }}
                </span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
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
                <span class="chip-pill chip-pill--compact">{{
                  formatViewerFormatStatus(format)
                }}</span>
              </div>
              <p>{{ formatViewerFormatNote(format) }}</p>
            </article>
          </div>
        </div>
        <p v-if="!plannedDocumentFormats.length" class="viewer-panel__empty">
          Все заявленные документные форматы уже открываются в этом экране без отдельного обходного
          сценария.
        </p>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Почему это удобно</p>
        <h2>
          Один и тот же экран подстраивается под разные типы документов, а не заставляет
          переключаться между разными страницами.
        </h2>
        <div class="architecture-grid">
          <article class="architecture-card">
            <strong>Единый вход</strong>
            <p>PDF, таблицы, презентации, EPUB и базы данных открываются из одного места.</p>
          </article>
          <article class="architecture-card">
            <strong>Подходящий режим</strong>
            <p>
              Jack сам выбирает, показать ли текст, таблицу, HTML, листы, слайды или структуру базы.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Поиск по содержимому</strong>
            <p>Можно быстро найти нужный фрагмент, не думая о внутреннем формате документа.</p>
          </article>
          <article class="architecture-card">
            <strong>Быстрая редактура</strong>
            <p>
              Для текстовых форматов доступна рабочая копия, которую можно поправить и сразу открыть
              в editor.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Честное отображение</strong>
            <p>
              Если формат нельзя показать идеально, Jack всё равно даёт полезное и понятное
              представление вместо пустого экрана.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Один сценарий</strong>
            <p>
              Загрузка, полноэкранный режим, поиск и сводка работают одинаково для разных семейств
              файлов.
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
