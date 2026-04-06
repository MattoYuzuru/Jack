<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useConverterWorkspace } from '../features/converter/composables/useConverterWorkspace'

const fileInput = ref<HTMLInputElement | null>(null)
const isDragActive = ref(false)

const {
  prepared,
  result,
  resultHistory,
  availablePresets,
  availableTargets,
  activeTarget,
  activePreset,
  isLoading,
  isConverting,
  isCancelling,
  canRetry,
  hasResultHistory,
  errorMessage,
  processingMessage,
  selectedTargetExtension,
  selectedPresetId,
  quality,
  backgroundColor,
  selectedVideoCodec,
  selectedMediaResolution,
  selectedTargetFps,
  selectedVideoBitrateKbps,
  selectedAudioBitrateKbps,
  showMediaControls,
  showVideoCodecControl,
  showResolutionControl,
  showFpsControl,
  showVideoBitrateControl,
  showAudioBitrateControl,
  availableVideoCodecOptions,
  resolvedAudioCodec,
  resolutionOptions,
  fpsOptions,
  videoBitrateOptions,
  audioBitrateOptions,
  converterAcceptAttribute,
  imageScenarios,
  documentScenarios,
  mediaScenarios,
  activeJobId,
  activeJobStatus,
  activeJobProgressPercent,
  selectFile,
  clearSelection,
  convert,
  retryLastConversion,
  cancelConversion,
  selectResult,
  downloadResult,
  downloadHistoryEntry,
} = useConverterWorkspace()

const statusLabels: Record<string, string> = {
  QUEUED: 'В очереди',
  RUNNING: 'В работе',
  COMPLETED: 'Готово',
  FAILED: 'Ошибка',
  CANCELLED: 'Остановлено',
}

const sourceFacts = computed(() => {
  if (!prepared.value) {
    return []
  }

  return [
    { label: 'Файл', value: prepared.value.file.name },
    {
      label: 'Размер',
      value: new Intl.NumberFormat('ru-RU').format(prepared.value.file.size) + ' байт',
    },
    { label: 'Формат', value: prepared.value.source.label },
    { label: 'Режим обработки', value: 'Готов к конвертации' },
  ]
})

const currentScenario = computed(() =>
  prepared.value?.scenarios.find(
    (scenario) => scenario.targetExtension === selectedTargetExtension.value,
  ),
)
const currentStatusLabel = computed(() =>
  activeJobStatus.value
    ? (statusLabels[activeJobStatus.value] ?? activeJobStatus.value)
    : 'Ожидание',
)
const progressWidth = computed(
  () => `${Math.max(0, Math.min(activeJobProgressPercent.value, 100))}%`,
)
const historyEntries = computed(() => resultHistory.value.slice(0, 6))
const hasAudioPreview = computed(() => result.value?.previewMimeType.startsWith('audio/') ?? false)
const activeLimitations = computed(() => {
  if (!prepared.value || !currentScenario.value) {
    return []
  }

  const limitations: string[] = []
  const sourceExtension = prepared.value.source.extension
  const targetExtension = selectedTargetExtension.value

  if (currentScenario.value.family === 'media') {
    limitations.push(
      'Формат результата задаёт контейнер, а кодек, битрейт, разрешение и FPS настраиваются отдельно ниже.',
    )
  }

  if (sourceExtension === 'pdf' && targetExtension === 'docx') {
    limitations.push(
      'PDF -> DOCX переносит текстовый поток, но сложная вёрстка, колонки и positioned blocks могут измениться.',
    )
  }

  if (
    sourceExtension === 'pdf' &&
    ['docx', 'txt', 'xlsx', 'csv', 'pptx'].includes(targetExtension)
  ) {
    limitations.push(
      'Если исходный PDF отсканирован и без текстового слоя, сначала понадобится OCR: текущий export это явно подсветит.',
    )
  }

  if (targetExtension === 'csv') {
    limitations.push(
      'CSV остаётся flattened table export: formulas, styling, comments и multi-sheet structure не переносятся полностью.',
    )
  }

  return limitations
})

function formatScenarioStatusLabel(available: boolean): string {
  return available ? 'Готово к запуску' : 'Временно недоступно'
}

function formatTargetStatusLabel(family: string, available: boolean): string {
  if (!available) {
    return 'Недоступно'
  }

  switch (family) {
    case 'image':
      return 'Изображение'
    case 'document':
      return 'Документ'
    default:
      return 'Медиа'
  }
}

function formatPresetStatusLabel(presetId: string): string {
  switch (presetId) {
    case 'original':
      return 'Без уменьшения'
    case 'web-balanced':
      return 'Для веба'
    case 'email-attachment':
      return 'Для вложений'
    case 'thumbnail':
      return 'Для миниатюр'
    default:
      return 'Профиль'
  }
}

function formatPresetDescription(
  preset: (typeof availablePresets.value)[number] | null | undefined,
): string {
  switch (preset?.id) {
    case 'original':
      return 'Сохраняет исходную геометрию и подходит, когда нужно только сменить формат.'
    case 'web-balanced':
      return 'Сбалансированный профиль для сайтов, CMS и карточек товаров.'
    case 'email-attachment':
      return 'Помогает уложиться в ограничения почты и мессенджеров.'
    case 'thumbnail':
      return 'Подходит для превью, миниатюр и лёгких карточек.'
    default:
      return preset?.detail ?? 'Подбирает разумный баланс между размером и качеством.'
  }
}

function formatRuntimeLabel(value: string | null | undefined): string {
  if (!value) {
    return 'Локально'
  }

  const normalized = value.trim().toLowerCase()

  if (
    normalized.includes('jpeg') ||
    normalized.includes('png') ||
    normalized.includes('webp') ||
    normalized.includes('avif') ||
    normalized.includes('tiff') ||
    normalized.includes('ico') ||
    normalized.includes('svg') ||
    normalized.includes('heic') ||
    normalized.includes('imagemagick') ||
    normalized.includes('potrace')
  ) {
    return 'Обработка изображения'
  }

  if (
    normalized.includes('media') ||
    normalized.includes('ffmpeg') ||
    normalized.includes('mp4') ||
    normalized.includes('mp3') ||
    normalized.includes('slideshow')
  ) {
    return 'Обработка медиа'
  }

  if (
    normalized.includes('pdf') ||
    normalized.includes('doc') ||
    normalized.includes('xlsx') ||
    normalized.includes('sheet') ||
    normalized.includes('slide') ||
    normalized.includes('spreadsheet') ||
    normalized.includes('epub') ||
    normalized.includes('sqlite')
  ) {
    return 'Подготовка документа'
  }

  return value
}

function formatScenarioDescription(
  scenario: (typeof imageScenarios.value)[number] | null | undefined,
): string {
  if (!scenario) {
    return ''
  }

  const sourceExtension = scenario.sourceExtension.toUpperCase()
  const targetExtension = scenario.targetExtension.toUpperCase()

  if (scenario.family === 'media') {
    if (targetExtension === 'GIF') {
      return `${sourceExtension} можно превратить в короткую анимацию для превью, инструкции или публикации без звука.`
    }

    if (['MP3', 'WAV', 'AAC', 'M4A', 'FLAC'].includes(targetExtension)) {
      return `${sourceExtension} можно быстро перевести в аудиоформат и забрать только звуковую дорожку.`
    }

    return `${sourceExtension} можно перевести в ${targetExtension} с настройкой кодека, битрейта, разрешения и частоты кадров.`
  }

  if (scenario.sourceExtension === 'pdf' && scenario.targetExtension === 'docx') {
    return 'Подходит для переноса текста из PDF в редактируемый документ, если важнее содержание, а не точная вёрстка.'
  }

  if (
    scenario.sourceExtension === 'pdf' &&
    ['docx', 'txt', 'xlsx', 'csv', 'pptx'].includes(scenario.targetExtension)
  ) {
    return 'Если PDF состоит из сканов без текстового слоя, сначала может понадобиться OCR.'
  }

  if (scenario.targetExtension === 'csv') {
    return 'Удобно для выгрузки таблиц в простой табличный формат без оформления и формул.'
  }

  if (scenario.targetExtension === 'pdf') {
    return `${sourceExtension} можно перевести в PDF, чтобы документ было удобнее отправлять, хранить и печатать.`
  }

  return `${sourceExtension} можно перевести в ${targetExtension} и сразу проверить результат перед скачиванием.`
}

function formatBytes(value: number): string {
  return new Intl.NumberFormat('ru-RU').format(value) + ' байт'
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function openFilePicker() {
  fileInput.value?.click()
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
</script>

<template>
  <main class="workspace-shell converter-workspace">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack · Converter</p>
          <p class="brand-lockup__title">Конвертация файлов</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <RouterLink class="back-link" to="/">На главную</RouterLink>
        <span class="chip-pill">Конвертация файлов</span>
        <span class="chip-pill chip-pill--accent">Изображения, документы, медиа</span>
      </div>
    </header>

    <section class="converter-hero-grid">
      <article class="panel-surface converter-hero-copy">
        <p class="eyebrow">Быстрая конвертация без ручных обходов</p>
        <h1>Выбери файл, формат назначения и сразу получи готовый результат с предпросмотром.</h1>
        <p class="lead">
          Конвертер закрывает и привычные сценарии вроде `PNG -> JPG`, и более сложные задачи: `PDF
          -> DOCX`, `PPTX -> MP4`, `RAW -> JPG`, `video -> audio` и другие. На экране сразу видно,
          что доступно для выбранного файла, какие ограничения есть у формата и какой результат
          получится на выходе.
        </p>

        <div class="converter-signal-row">
          <span class="chip-pill">HEIC, TIFF, RAW</span>
          <span class="chip-pill">DOC, DOCX, PDF, XLSX, PPTX</span>
          <span class="chip-pill">MP4, WebM, GIF, MP3</span>
          <span class="chip-pill">Готовые пресеты</span>
          <span class="chip-pill">Предпросмотр результата</span>
          <span class="chip-pill">Повторное скачивание</span>
        </div>
      </article>

      <article class="panel-surface converter-system-card">
        <p class="eyebrow">Подходящие направления</p>
        <h2>Ниже показаны самые близкие сценарии для изображений, документов и медиа.</h2>

        <div class="scenario-list" aria-label="Доступные сценарии конвертации">
          <article v-for="scenario in imageScenarios" :key="scenario.id" class="scenario-item">
            <div>
              <h3>{{ scenario.label }}</h3>
              <p>{{ formatScenarioDescription(scenario) }}</p>
            </div>
            <span class="chip-pill chip-pill--compact chip-pill--accent">{{
              formatScenarioStatusLabel(scenario.available)
            }}</span>
          </article>
        </div>

        <div class="scenario-list scenario-list--secondary" aria-label="Документные сценарии">
          <article v-for="scenario in documentScenarios" :key="scenario.id" class="scenario-item">
            <div>
              <h3>{{ scenario.label }}</h3>
              <p>{{ formatScenarioDescription(scenario) }}</p>
            </div>
            <span class="chip-pill chip-pill--compact">{{
              formatScenarioStatusLabel(scenario.available)
            }}</span>
          </article>
        </div>

        <div class="scenario-list scenario-list--secondary" aria-label="Медиа-сценарии">
          <article v-for="scenario in mediaScenarios" :key="scenario.id" class="scenario-item">
            <div>
              <h3>{{ scenario.label }}</h3>
              <p>{{ formatScenarioDescription(scenario) }}</p>
            </div>
            <span class="chip-pill chip-pill--compact">{{
              formatScenarioStatusLabel(scenario.available)
            }}</span>
          </article>
        </div>
      </article>
    </section>

    <section class="converter-main-grid">
      <article class="panel-surface converter-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Исходный файл</p>
            <h2>Загрузи материал и выбери, во что его превратить.</h2>
          </div>

          <button v-if="prepared" type="button" class="action-button" @click="clearSelection">
            Очистить
          </button>
        </div>

        <input
          ref="fileInput"
          class="file-input"
          type="file"
          :accept="converterAcceptAttribute"
          @change="onFileChange"
        />

        <button
          type="button"
          class="converter-dropzone"
          :class="{ 'converter-dropzone--active': isDragActive }"
          @click="openFilePicker"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <span class="converter-dropzone__badge">Перетащить или выбрать</span>
          <strong>Перетащи изображение, документ, PDF, видео или аудио.</strong>
          <span>
            После выбора Jack покажет только совместимые форматы назначения, а ниже откроет
            настройки качества, размера и кодеков там, где это действительно нужно.
          </span>
        </button>

        <p v-if="errorMessage" class="status-message status-message--error">{{ errorMessage }}</p>
        <p v-else-if="isLoading" class="status-message">
          {{ processingMessage }}
        </p>
        <p v-else-if="isConverting || processingMessage" class="status-message">
          {{ processingMessage }}
        </p>

        <div v-if="prepared" class="converter-stack">
          <div class="facts-grid">
            <article v-for="fact in sourceFacts" :key="fact.label" class="fact-card">
              <span>{{ fact.label }}</span>
              <strong>{{ fact.value }}</strong>
            </article>
          </div>

          <div class="control-cluster">
            <div>
              <p class="control-label">Формат результата</p>
              <div class="target-grid">
                <button
                  v-for="target in availableTargets"
                  :key="target.extension"
                  type="button"
                  class="target-chip"
                  :class="{ 'target-chip--active': selectedTargetExtension === target.extension }"
                  @click="selectedTargetExtension = target.extension"
                >
                  <span>{{ target.label }}</span>
                  <small>{{ formatTargetStatusLabel(target.family, target.available) }}</small>
                </button>
              </div>
            </div>

            <div>
              <p class="control-label">Профиль качества</p>
              <div class="preset-grid">
                <button
                  v-for="preset in availablePresets"
                  :key="preset.id"
                  type="button"
                  class="target-chip target-chip--preset"
                  :class="{ 'target-chip--active': selectedPresetId === preset.id }"
                  @click="selectedPresetId = preset.id"
                >
                  <span>{{ preset.label }}</span>
                  <small>{{ formatPresetStatusLabel(preset.id) }}</small>
                </button>
              </div>
            </div>

            <div v-if="currentScenario" class="scenario-callout">
              <p class="control-label">Активный сценарий</p>
              <h3>{{ currentScenario.label }}</h3>
              <p>{{ formatScenarioDescription(currentScenario) }}</p>
            </div>

            <div v-if="activeLimitations.length" class="scenario-callout scenario-callout--limits">
              <p class="control-label">Что важно учесть</p>
              <h3>Что важно учитывать до запуска</h3>
              <p v-for="item in activeLimitations" :key="item">{{ item }}</p>
            </div>

            <div class="scenario-callout scenario-callout--job">
              <p class="control-label">Состояние обработки</p>
              <h3>{{ currentStatusLabel }}</h3>
              <p>
                {{
                  activeJobId
                    ? `Задача ${activeJobId} сейчас обрабатывает конвертацию.`
                    : 'После запуска здесь появится идентификатор текущей задачи.'
                }}
              </p>
              <div class="job-progress">
                <div class="job-progress__bar" :style="{ width: progressWidth }"></div>
              </div>
              <div class="job-meta-row">
                <span>{{ activeJobProgressPercent }}%</span>
                <span>{{ activeJobStatus || 'IDLE' }}</span>
              </div>
            </div>

            <div v-if="activePreset" class="scenario-callout">
              <p class="control-label">Активный пресет</p>
              <h3>{{ activePreset.label }}</h3>
              <p>{{ formatPresetDescription(activePreset) }}</p>
            </div>

            <label v-if="showVideoCodecControl" class="form-field">
              <span class="control-label">Видеокодек</span>
              <select v-model="selectedVideoCodec" class="surface-select">
                <option
                  v-for="option in availableVideoCodecOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label v-if="showResolutionControl" class="form-field">
              <span class="control-label">Разрешение</span>
              <select v-model="selectedMediaResolution" class="surface-select">
                <option
                  v-for="option in resolutionOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label v-if="showFpsControl" class="form-field">
              <span class="control-label">FPS</span>
              <select v-model="selectedTargetFps" class="surface-select">
                <option v-for="option in fpsOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label v-if="showVideoBitrateControl" class="form-field">
              <span class="control-label">Видеобитрейт</span>
              <select v-model="selectedVideoBitrateKbps" class="surface-select">
                <option
                  v-for="option in videoBitrateOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label v-if="showAudioBitrateControl" class="form-field">
              <span class="control-label">Аудиобитрейт</span>
              <select v-model="selectedAudioBitrateKbps" class="surface-select">
                <option
                  v-for="option in audioBitrateOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>

            <div v-if="showMediaControls && resolvedAudioCodec" class="scenario-callout">
              <p class="control-label">Аудиокодек результата</p>
              <h3>{{ resolvedAudioCodec }}</h3>
              <p>
                Формат файла задаёт контейнер, а кодек показан отдельно, чтобы было понятно, что
                именно получишь на выходе.
              </p>
            </div>

            <label v-if="activeTarget?.supportsQuality" class="form-field">
              <span class="control-label">Качество</span>
              <div class="range-row">
                <input v-model.number="quality" type="range" min="0.55" max="1" step="0.01" />
                <strong>{{ Math.round(quality * 100) }}%</strong>
              </div>
            </label>

            <label v-if="activeTarget && !activeTarget.supportsTransparency" class="form-field">
              <span class="control-label">Фон вместо прозрачности</span>
              <div class="color-row">
                <input v-model="backgroundColor" type="color" />
                <span>{{ backgroundColor }}</span>
              </div>
            </label>
          </div>

          <div class="action-row">
            <button
              type="button"
              class="action-button action-button--accent action-button--wide"
              :disabled="isConverting || !selectedTargetExtension"
              @click="convert"
            >
              Convert to {{ activeTarget?.label ?? 'target' }}
            </button>
            <button
              v-if="isConverting"
              type="button"
              class="action-button action-button--wide"
              :disabled="isCancelling"
              @click="cancelConversion"
            >
              {{ isCancelling ? 'Останавливаю...' : 'Остановить' }}
            </button>
            <button
              v-else
              type="button"
              class="action-button action-button--wide"
              :disabled="!canRetry"
              @click="retryLastConversion"
            >
              Повторить последний запуск
            </button>
          </div>
        </div>
      </article>

      <article class="panel-surface converter-panel converter-panel--result">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Результат</p>
            <h2>Предпросмотр итогового файла и быстрый download.</h2>
          </div>

          <button
            v-if="result"
            type="button"
            class="action-button action-button--accent"
            @click="downloadResult"
          >
            Скачать
          </button>
        </div>

        <div v-if="result" class="result-stack">
          <div class="result-preview">
            <img
              v-if="result.previewKind === 'image'"
              :src="result.objectUrl"
              :alt="`Предпросмотр результата ${result.fileName}`"
            />
            <audio
              v-else-if="hasAudioPreview"
              class="result-preview__audio"
              :src="result.objectUrl"
              controls
            />
            <video
              v-else-if="result.previewKind === 'media'"
              class="result-preview__frame"
              :src="result.objectUrl"
              controls
            />
            <iframe
              v-else
              class="result-preview__frame"
              :src="result.objectUrl"
              title="Предпросмотр результата"
            />
          </div>

          <div class="result-facts">
            <article class="fact-card">
              <span>Файл</span>
              <strong>{{ result.fileName }}</strong>
            </article>
            <article class="fact-card">
              <span>Сценарий</span>
              <strong>{{ result.source.label }} -> {{ result.target.label }}</strong>
            </article>
            <article class="fact-card">
              <span>Тип результата</span>
              <strong>{{
                result.kind === 'document'
                  ? 'Документ'
                  : result.kind === 'media'
                    ? 'Медиа'
                    : 'Изображение'
              }}</strong>
            </article>
            <article class="fact-card">
              <span>Профиль</span>
              <strong>{{ result.preset.label }}</strong>
            </article>
            <article
              v-for="fact in [...result.resultFacts, ...result.sourceFacts]"
              :key="`${fact.label}-${fact.value}`"
              class="fact-card"
            >
              <span>{{ fact.label }}</span>
              <strong>{{ fact.value }}</strong>
            </article>
            <article class="fact-card">
              <span>Размер файла</span>
              <strong>{{ formatBytes(result.blob.size) }}</strong>
            </article>
            <article class="fact-card">
              <span>Задача</span>
              <strong>{{ result.backendJobId ?? 'Без очереди' }}</strong>
            </article>
            <article class="fact-card">
              <span>Режим обработки</span>
              <strong>{{ formatRuntimeLabel(result.backendRuntimeLabel) }}</strong>
            </article>
            <article class="fact-card">
              <span>Собрано</span>
              <strong>{{ formatDate(result.createdAt) }}</strong>
            </article>
          </div>

          <p
            v-for="warning in result.warnings"
            :key="warning"
            class="status-message status-message--warning"
          >
            {{ warning }}
          </p>

          <section v-if="hasResultHistory" class="history-stack" aria-label="История конвертаций">
            <div class="panel-header panel-header--compact">
              <div>
                <p class="eyebrow">История сессии</p>
                <h3>Недавние результаты можно открыть повторно или скачать ещё раз.</h3>
              </div>
            </div>

            <article
              v-for="entry in historyEntries"
              :key="entry.id"
              class="history-item"
              :class="{ 'history-item--active': result?.id === entry.id }"
            >
              <div>
                <strong>{{ entry.fileName }}</strong>
                <p>
                  {{ entry.sourceFileName }} · {{ entry.source.label }} -> {{ entry.target.label }}
                </p>
                <span>{{ formatDate(entry.createdAt) }}</span>
              </div>

              <div class="history-actions">
                <button type="button" class="action-button" @click="selectResult(entry.id)">
                  Открыть
                </button>
                <button
                  type="button"
                  class="action-button action-button--accent"
                  @click="downloadHistoryEntry(entry.id)"
                >
                  Скачать ещё раз
                </button>
              </div>
            </article>
          </section>
        </div>

        <div v-else class="result-placeholder">
          <p class="eyebrow">Результат появится здесь</p>
          <h3>Сначала выбери файл и направление конвертации.</h3>
          <p>
            После успешной обработки здесь появятся предпросмотр, основные параметры результата и
            история последних выгрузок.
          </p>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.converter-hero-grid,
.converter-main-grid {
  display: grid;
  gap: 22px;
  margin-top: 22px;
}

.converter-hero-grid {
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.95fr);
}

.converter-main-grid {
  grid-template-columns: minmax(0, 1.05fr) minmax(340px, 0.95fr);
  align-items: start;
}

.converter-hero-copy,
.converter-system-card,
.converter-panel {
  padding: 30px;
}

h1,
.converter-system-card h2,
.converter-panel h2,
.result-placeholder h3,
.scenario-callout h3,
.history-stack h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  letter-spacing: -0.04em;
}

h1 {
  margin-top: 16px;
  max-width: 13ch;
  font-size: clamp(2.8rem, 4.3vw, 4.7rem);
  line-height: 0.94;
}

.converter-system-card h2,
.converter-panel h2,
.result-placeholder h3,
.scenario-callout h3,
.history-stack h3 {
  font-size: clamp(1.7rem, 2.2vw, 2.45rem);
  line-height: 1;
}

.lead,
.scenario-item p,
.scenario-callout p,
.result-placeholder p,
.history-item p,
.history-item span {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.98rem;
}

.lead {
  margin-top: 20px;
  max-width: 60ch;
  font-size: 1.04rem;
}

.converter-signal-row,
.target-grid,
.preset-grid,
.result-facts,
.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.converter-signal-row {
  margin-top: 24px;
}

.scenario-list,
.converter-stack,
.result-stack,
.control-cluster,
.history-stack {
  display: grid;
  gap: 16px;
}

.scenario-list {
  margin-top: 22px;
}

.scenario-list--secondary {
  margin-top: 16px;
}

.scenario-item,
.scenario-callout,
.converter-dropzone,
.fact-card,
.result-preview,
.result-placeholder {
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.scenario-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
}

.scenario-item h3 {
  margin: 0;
  color: var(--text-main);
  font-size: 1rem;
}

.scenario-item p {
  margin-top: 8px;
  max-width: 44ch;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.panel-header--compact {
  justify-content: flex-start;
}

.file-input {
  display: none;
}

.converter-dropzone {
  display: grid;
  gap: 12px;
  width: 100%;
  margin-top: 24px;
  padding: 24px;
  border: 0;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}

.converter-dropzone:hover,
.converter-dropzone--active {
  transform: translateY(-2px);
  box-shadow: var(--shadow-floating);
}

.converter-dropzone strong {
  font-size: 1.08rem;
}

.converter-dropzone span:last-child {
  color: var(--text-soft);
}

.converter-dropzone__badge {
  display: inline-flex;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(29, 92, 85, 0.08);
  color: var(--accent-cool-strong);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.status-message {
  margin: 16px 0 0;
  color: var(--text-soft);
  font-size: 0.94rem;
}

.status-message--error {
  color: #8f3d24;
}

.status-message--warning {
  margin: 0;
  color: #8f5d17;
}

.facts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.fact-card {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.fact-card span,
.control-label {
  color: var(--text-soft);
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.fact-card strong {
  color: var(--accent-cool-strong);
  font-size: 1rem;
}

.target-chip {
  display: grid;
  gap: 6px;
  min-width: 120px;
  padding: 14px 16px;
  border: 0;
  border-radius: 22px;
  background: linear-gradient(145deg, rgba(255, 251, 244, 0.92), rgba(225, 215, 201, 0.92));
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
  cursor: pointer;
  text-align: left;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    color 180ms ease;
}

.target-chip--preset {
  min-width: 150px;
}

.target-chip:hover,
.target-chip--active {
  transform: translateY(-2px);
  box-shadow: var(--shadow-floating);
  color: var(--accent-cool-strong);
}

.target-chip small {
  color: var(--text-soft);
  font-size: 0.78rem;
}

.scenario-callout,
.form-field {
  padding: 18px;
}

.scenario-callout--job {
  gap: 12px;
}

.form-field {
  display: grid;
  gap: 12px;
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.22);
}

.surface-select {
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--text-main);
  font: inherit;
  box-shadow: var(--shadow-pressed);
}

.job-progress {
  overflow: hidden;
  height: 12px;
  border-radius: 999px;
  background: rgba(29, 92, 85, 0.12);
}

.job-progress__bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(29, 92, 85, 0.88), rgba(224, 125, 78, 0.9));
  transition: width 180ms ease;
}

.job-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-soft);
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.range-row,
.color-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.range-row input,
.color-row input {
  width: 100%;
}

.color-row input[type='color'] {
  max-width: 68px;
  height: 42px;
  padding: 0;
  border: 0;
  border-radius: 14px;
  background: transparent;
}

.action-button--wide {
  width: 100%;
}

.action-row .action-button--wide {
  flex: 1 1 220px;
}

.converter-panel--result {
  min-height: 100%;
}

.result-preview {
  display: grid;
  place-items: center;
  min-height: 320px;
  padding: 18px;
  background:
    linear-gradient(45deg, rgba(16, 36, 38, 0.04) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(16, 36, 38, 0.04) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(16, 36, 38, 0.04) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(16, 36, 38, 0.04) 75%), var(--surface-muted);
  background-size: 24px 24px;
  background-position:
    0 0,
    0 12px,
    12px -12px,
    -12px 0;
}

.result-preview img {
  max-width: 100%;
  max-height: 420px;
  border-radius: 22px;
  box-shadow: var(--shadow-floating);
}

.result-preview__frame {
  width: 100%;
  min-height: 420px;
  border: 0;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.75);
  box-shadow: var(--shadow-floating);
}

.result-preview__audio {
  width: min(100%, 560px);
}

.result-placeholder {
  display: grid;
  gap: 14px;
  place-items: start;
  min-height: 420px;
  padding: 28px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.28);
  box-shadow: var(--shadow-pressed);
}

.history-item--active {
  box-shadow: var(--shadow-floating);
}

.history-item strong {
  display: block;
  color: var(--accent-cool-strong);
  font-size: 1rem;
}

.history-item p {
  margin-top: 6px;
}

.history-item span {
  display: inline-block;
  margin-top: 10px;
  font-size: 0.85rem;
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 1180px) {
  .converter-hero-grid,
  .converter-main-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .converter-hero-copy,
  .converter-system-card,
  .converter-panel {
    padding: 24px;
  }

  .panel-header,
  .scenario-item,
  .history-item {
    flex-direction: column;
  }

  .facts-grid {
    grid-template-columns: 1fr;
  }

  .target-grid,
  .preset-grid,
  .result-facts,
  .converter-signal-row,
  .action-row,
  .history-actions {
    flex-direction: column;
  }
}
</style>
