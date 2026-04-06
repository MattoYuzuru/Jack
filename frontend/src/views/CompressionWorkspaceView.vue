<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useCompressionWorkspace } from '../features/compression/composables/useCompressionWorkspace'
import type {
  CompressionSourceFormatDefinition,
  CompressionTargetFormatDefinition,
} from '../features/compression/domain/compression-registry'

const fileInput = ref<HTMLInputElement | null>(null)
const isDragActive = ref(false)

const {
  prepared,
  result,
  resultHistory,
  availableModes,
  availableTargets,
  activeMode,
  activeTarget,
  targetOptions,
  isLoading,
  isCompressing,
  isCancelling,
  errorMessage,
  processingMessage,
  compressionAcceptAttribute,
  selectedModeId,
  selectedTargetExtension,
  targetSizeValue,
  targetSizeUnit,
  selectedResolution,
  quality,
  backgroundColor,
  selectedTargetFps,
  selectedVideoBitrateKbps,
  selectedAudioBitrateKbps,
  activeJobId,
  activeJobStatus,
  activeJobProgressPercent,
  showTargetSizeControl,
  showResolutionControl,
  showQualityControl,
  showBackgroundColorControl,
  showVideoBitrateControl,
  showAudioBitrateControl,
  showFpsControl,
  hasResultHistory,
  canRetry,
  resolutionOptions,
  fpsOptions,
  videoBitrateOptions,
  audioBitrateOptions,
  selectFile,
  clearSelection,
  compress,
  retryLastCompression,
  cancelCompression,
  selectResult,
  downloadResult,
} = useCompressionWorkspace()

const statusLabels: Record<string, string> = {
  QUEUED: 'В очереди',
  RUNNING: 'В работе',
  COMPLETED: 'Готово',
  FAILED: 'Ошибка',
  CANCELLED: 'Остановлено',
}

const currentStatusLabel = computed(() =>
  activeJobStatus.value
    ? (statusLabels[activeJobStatus.value] ?? activeJobStatus.value)
    : 'Ожидание',
)
const progressWidth = computed(
  () => `${Math.max(0, Math.min(activeJobProgressPercent.value, 100))}%`,
)
const historyEntries = computed(() => resultHistory.value.slice(0, 5))
const sourceFacts = computed(() => {
  if (!prepared.value) {
    return []
  }

  return [
    { label: 'Файл', value: prepared.value.file.name },
    { label: 'Категория', value: prepared.value.source.family.toUpperCase() },
    { label: 'Размер', value: formatBytes(prepared.value.file.size) },
    { label: 'Режим обработки', value: 'Готов к сжатию' },
  ]
})

function formatModeLabel(modeId: string): string {
  switch (modeId) {
    case 'maximum':
      return 'Максимальное уменьшение'
    case 'target-size':
      return 'Лимит размера'
    case 'custom':
      return 'Ручная настройка'
    default:
      return modeId
  }
}

function formatModeDetail(modeId: string): string {
  switch (modeId) {
    case 'maximum':
      return 'Jack подберёт самый лёгкий практический вариант без ручного перебора настроек.'
    case 'target-size':
      return 'Сервис будет искать лучший результат, который помещается в заданный лимит.'
    case 'custom':
      return 'Можно вручную выбрать формат результата, качество, битрейт, размер кадра и FPS.'
    default:
      return ''
  }
}

function formatModeAccents(modeId: string): string[] {
  switch (modeId) {
    case 'maximum':
      return ['Минимальный вес', 'Автовыбор']
    case 'target-size':
      return ['Лимит', 'Best effort']
    case 'custom':
      return ['Вручную', 'Качество']
    default:
      return []
  }
}

function formatSourceDescription(
  source: CompressionSourceFormatDefinition | null | undefined,
): string {
  if (!source) {
    return ''
  }

  if (source.family === 'image') {
    return 'Jack подберёт более лёгкий формат и уменьшит размер там, где это действительно помогает.'
  }

  if (source.family === 'media') {
    return 'Можно удержать файл в лимите за счёт контейнера, битрейта, разрешения и частоты кадров.'
  }

  return 'Можно подобрать более компактный контейнер и битрейт без ручного перебора вариантов.'
}

function formatTargetDescription(
  target: CompressionTargetFormatDefinition | null | undefined,
): string {
  if (!target) {
    return ''
  }

  if (target.family === 'image') {
    return `${target.label} подходит для сжатия изображений с контролем качества и размера.`
  }

  if (target.family === 'media') {
    return `${target.label} подходит для видео с ограничением по битрейту, размеру кадра и частоте.`
  }

  return `${target.label} подходит для компактного аудио с контролем битрейта.`
}

function formatRuntimeLabel(value: string | null | undefined): string {
  if (!value) {
    return 'Автоподбор'
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
    return 'Сжатие изображения'
  }

  if (
    normalized.includes('media') ||
    normalized.includes('ffmpeg') ||
    normalized.includes('mp4') ||
    normalized.includes('mp3') ||
    normalized.includes('wav')
  ) {
    return 'Сжатие медиа'
  }

  return 'Подобранный вариант'
}

function formatBytes(value: number): string {
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }

  return `${(value / (1024 * 1024)).toFixed(2)} MB`
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
  <main class="workspace-shell compression-workspace">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack · Compression</p>
          <p class="brand-lockup__title">Сжатие файлов</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <RouterLink class="back-link" to="/">На главную</RouterLink>
        <span class="chip-pill">Сжатие файлов</span>
        <span class="chip-pill chip-pill--accent">Под размер или на максимум</span>
      </div>
    </header>

    <section class="compression-hero-grid">
      <article class="panel-surface compression-hero-copy">
        <p class="eyebrow">Когда важен итоговый вес файла</p>
        <h1>
          Сожми изображение, видео или аудио под лимит загрузки либо до практического минимума.
        </h1>
        <p class="lead">
          Этот модуль нужен для реальных ограничений: вложения в письме, лимит CMS, мессенджеры,
          отчёты и выгрузки. Вместо ручного перебора настроек можно сразу выбрать цель и сравнить,
          как менялся вес файла на каждом шаге.
        </p>

        <div class="compression-signal-row">
          <span class="chip-pill">Изображения</span>
          <span class="chip-pill">Видео</span>
          <span class="chip-pill">Аудио</span>
          <span class="chip-pill">Лимит размера</span>
          <span class="chip-pill">Максимальное уменьшение</span>
          <span class="chip-pill">История попыток</span>
        </div>
      </article>

      <article class="panel-surface compression-system-card">
        <p class="eyebrow">Режимы работы</p>
        <h2>Сначала цель по весу, затем подходящая стратегия для выбранного типа файла.</h2>

        <div class="mode-list">
          <article v-for="mode in availableModes" :key="mode.id" class="mode-item">
            <div>
              <h3>{{ formatModeLabel(mode.id) }}</h3>
              <p>{{ formatModeDetail(mode.id) }}</p>
            </div>
            <div class="mode-item__accents">
              <span
                v-for="accent in formatModeAccents(mode.id)"
                :key="accent"
                class="chip-pill chip-pill--compact"
              >
                {{ accent }}
              </span>
            </div>
          </article>
        </div>
      </article>
    </section>

    <section class="compression-grid">
      <article class="panel-surface compression-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Исходный файл</p>
            <h2>Выбери, что нужно уменьшить.</h2>
          </div>
          <button type="button" class="action-button" @click="openFilePicker">Выбрать файл</button>
        </div>

        <input
          ref="fileInput"
          class="file-input"
          type="file"
          :accept="compressionAcceptAttribute"
          @change="onFileChange"
        />

        <div
          class="compression-dropzone"
          :class="{ 'compression-dropzone--active': isDragActive }"
          @click="openFilePicker"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <span class="compression-dropzone__badge">Перетащить или выбрать</span>
          <strong>{{
            prepared ? prepared.file.name : 'Перетащи изображение, видео или аудиофайл'
          }}</strong>
          <span>
            {{
              prepared
                ? formatSourceDescription(prepared.source)
                : 'После выбора файла Jack подскажет подходящие форматы и режимы сжатия для этого типа данных.'
            }}
          </span>
        </div>

        <div v-if="prepared" class="fact-grid">
          <article v-for="fact in sourceFacts" :key="fact.label" class="fact-chip">
            <span>{{ fact.label }}</span>
            <strong>{{ fact.value }}</strong>
          </article>
        </div>

        <div v-if="prepared" class="target-badges">
          <span
            v-for="target in availableTargets"
            :key="target.extension"
            class="chip-pill chip-pill--compact"
          >
            {{ target.label }}
          </span>
        </div>
      </article>

      <article class="panel-surface compression-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Настройки</p>
            <h2>Режим и ограничения</h2>
          </div>
          <button type="button" class="action-button" :disabled="!prepared" @click="clearSelection">
            Сбросить
          </button>
        </div>

        <div class="mode-toggle">
          <button
            v-for="mode in availableModes"
            :key="mode.id"
            type="button"
            class="mode-toggle__button"
            :class="{ 'mode-toggle__button--active': selectedModeId === mode.id }"
            @click="selectedModeId = mode.id"
          >
            <strong>{{ formatModeLabel(mode.id) }}</strong>
            <span>{{ formatModeDetail(mode.id) }}</span>
          </button>
        </div>

        <div class="form-grid">
          <label v-if="prepared && activeMode?.supportsTargetSelection" class="form-field">
            <span>Формат результата</span>
            <select v-model="selectedTargetExtension">
              <option v-for="option in targetOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showTargetSizeControl" class="form-field">
            <span>Целевой размер</span>
            <div class="compound-row">
              <input v-model="targetSizeValue" type="number" min="0.1" step="0.1" placeholder="5" />
              <select v-model="targetSizeUnit">
                <option value="MB">MB</option>
                <option value="KB">KB</option>
              </select>
            </div>
          </label>

          <label v-if="showResolutionControl" class="form-field">
            <span>Ограничение по размеру кадра</span>
            <select v-model="selectedResolution">
              <option v-for="option in resolutionOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showFpsControl" class="form-field">
            <span>Целевой FPS</span>
            <select v-model="selectedTargetFps">
              <option v-for="option in fpsOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showVideoBitrateControl" class="form-field">
            <span>Видеобитрейт</span>
            <select v-model="selectedVideoBitrateKbps">
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
            <span>Аудиобитрейт</span>
            <select v-model="selectedAudioBitrateKbps">
              <option
                v-for="option in audioBitrateOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showQualityControl" class="form-field">
            <span>Качество</span>
            <div class="range-row">
              <input v-model.number="quality" type="range" min="0.45" max="1" step="0.01" />
              <strong>{{ Math.round(quality * 100) }}%</strong>
            </div>
          </label>

          <label v-if="showBackgroundColorControl" class="form-field">
            <span>Фон вместо прозрачности</span>
            <div class="compound-row">
              <input v-model="backgroundColor" type="color" />
              <code>{{ backgroundColor }}</code>
            </div>
          </label>
        </div>

        <div class="job-progress">
          <div class="job-progress__labels">
            <span>Статус: {{ currentStatusLabel }}</span>
            <span>{{ activeJobProgressPercent }}%</span>
          </div>
          <div class="job-progress__track">
            <div class="job-progress__bar" :style="{ width: progressWidth }"></div>
          </div>
          <p v-if="processingMessage">{{ processingMessage }}</p>
          <p v-else-if="activeJobId">Задача: {{ activeJobId }}</p>
        </div>

        <div class="action-row">
          <button
            type="button"
            class="action-button action-button--accent"
            :disabled="!prepared || isLoading || isCompressing"
            @click="compress"
          >
            {{ isCompressing ? 'Сжимаю...' : 'Запустить сжатие' }}
          </button>
          <button
            type="button"
            class="action-button"
            :disabled="!canRetry"
            @click="retryLastCompression"
          >
            Повторить
          </button>
          <button
            type="button"
            class="action-button"
            :disabled="!activeJobId || isCancelling || !isCompressing"
            @click="cancelCompression"
          >
            {{ isCancelling ? 'Останавливаю...' : 'Остановить' }}
          </button>
        </div>

        <p v-if="errorMessage" class="status-note status-note--error">{{ errorMessage }}</p>
        <p v-else-if="activeTarget" class="status-note">
          Формат результата:
          <strong>{{ activeTarget.label }}</strong>
          · {{ formatTargetDescription(activeTarget) }}
        </p>
      </article>

      <article class="panel-surface compression-panel compression-panel--result">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Результат</p>
            <h2>Предпросмотр, снижение веса и предупреждения</h2>
          </div>
          <button
            type="button"
            class="action-button action-button--accent"
            :disabled="!result"
            @click="downloadResult()"
          >
            Скачать результат
          </button>
        </div>

        <div v-if="result" class="result-stack">
          <div class="result-preview">
            <img
              v-if="result.previewKind === 'image'"
              :src="result.previewObjectUrl"
              :alt="`Предпросмотр ${result.fileName}`"
            />
            <audio
              v-else-if="result.previewMimeType.startsWith('audio/')"
              class="result-preview__audio"
              :src="result.previewObjectUrl"
              controls
            />
            <video
              v-else
              class="result-preview__video"
              :src="result.previewObjectUrl"
              controls
              playsinline
            />
          </div>

          <div class="result-summary">
            <article class="result-metric">
              <span>Файл</span>
              <strong>{{ result.fileName }}</strong>
            </article>
            <article class="result-metric">
              <span>Режим</span>
              <strong>{{ formatModeLabel(result.mode) }}</strong>
            </article>
            <article class="result-metric">
              <span>Исходный размер</span>
              <strong>{{ formatBytes(result.sourceSizeBytes) }}</strong>
            </article>
            <article class="result-metric">
              <span>Размер результата</span>
              <strong>{{ formatBytes(result.resultSizeBytes) }}</strong>
            </article>
            <article class="result-metric">
              <span>Сжатие</span>
              <strong>{{ result.reductionPercent.toFixed(1) }}%</strong>
            </article>
            <article class="result-metric">
              <span>Цель</span>
              <strong>{{ result.targetMet ? 'Достигнута' : 'Лучший найденный вариант' }}</strong>
            </article>
            <article class="result-metric">
              <span>Задача</span>
              <strong>{{ result.backendJobId }}</strong>
            </article>
            <article class="result-metric">
              <span>Готово</span>
              <strong>{{ formatDate(result.createdAt) }}</strong>
            </article>
          </div>

          <div class="fact-columns">
            <div class="fact-column">
              <h3>Параметры сжатия</h3>
              <article
                v-for="fact in result.compressionFacts"
                :key="`compression-${fact.label}`"
                class="fact-chip"
              >
                <span>{{ fact.label }}</span>
                <strong>{{ fact.value }}</strong>
              </article>
            </div>

            <div class="fact-column">
              <h3>Исходный файл</h3>
              <article
                v-for="fact in result.sourceFacts"
                :key="`source-${fact.label}`"
                class="fact-chip"
              >
                <span>{{ fact.label }}</span>
                <strong>{{ fact.value }}</strong>
              </article>
            </div>

            <div class="fact-column">
              <h3>Результат</h3>
              <article
                v-for="fact in result.resultFacts"
                :key="`result-${fact.label}`"
                class="fact-chip"
              >
                <span>{{ fact.label }}</span>
                <strong>{{ fact.value }}</strong>
              </article>
            </div>
          </div>

          <div class="warning-stack">
            <article v-for="warning in result.warnings" :key="warning" class="warning-chip">
              {{ warning }}
            </article>
          </div>

          <div class="attempt-list">
            <article v-for="attempt in result.attempts" :key="attempt.label" class="attempt-item">
              <div>
                <strong>{{ attempt.label }}</strong>
                <p>{{ formatRuntimeLabel(attempt.runtimeLabel) }}</p>
              </div>
              <div class="attempt-item__facts">
                <span>{{ attempt.targetExtension.toUpperCase() }}</span>
                <span>{{ formatBytes(attempt.resultSizeBytes) }}</span>
                <span>{{ attempt.targetMet ? 'Уложился в цель' : 'Промежуточный вариант' }}</span>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="result-placeholder">
          <h3>Результат сжатия пока не собран.</h3>
          <p>
            Выбери файл, укажи режим и запусти сжатие. Здесь появятся итоговый результат, сравнение
            размеров и история попыток, чтобы было понятно, насколько файл удалось облегчить.
          </p>
        </div>
      </article>

      <article class="panel-surface compression-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">История</p>
            <h2>Последние результаты</h2>
          </div>
        </div>

        <div v-if="hasResultHistory" class="history-list">
          <button
            v-for="entry in historyEntries"
            :key="entry.id"
            type="button"
            class="history-item"
            :class="{ 'history-item--active': result?.id === entry.id }"
            @click="selectResult(entry.id)"
          >
            <div>
              <strong>{{ entry.fileName }}</strong>
              <p>{{ entry.mode }} · {{ entry.family.toUpperCase() }}</p>
            </div>
            <span>{{ formatBytes(entry.resultSizeBytes) }}</span>
          </button>
        </div>
        <div v-else class="result-placeholder result-placeholder--compact">
          <h3>История ещё пуста.</h3>
          <p>
            После первого успешного результата здесь можно будет быстро вернуться к прошлой версии.
          </p>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.compression-workspace {
  display: grid;
  gap: 24px;
}

.compression-hero-grid,
.compression-grid {
  display: grid;
  gap: 24px;
}

.compression-hero-grid {
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.95fr);
}

.compression-grid {
  grid-template-columns: minmax(320px, 0.9fr) minmax(320px, 0.95fr) minmax(0, 1.15fr);
  align-items: start;
}

.compression-hero-copy,
.compression-system-card,
.compression-panel {
  padding: 28px;
}

.compression-hero-copy h1,
.compression-system-card h2,
.compression-panel h2,
.result-placeholder h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  line-height: 1.02;
}

.lead,
.compression-hero-copy p,
.compression-system-card p,
.compression-panel p,
.result-placeholder p {
  margin: 0;
  color: var(--text-soft);
}

.compression-hero-copy,
.compression-system-card,
.compression-panel,
.result-stack,
.fact-columns,
.fact-column,
.mode-list,
.mode-item,
.mode-toggle,
.form-grid,
.panel-header,
.job-progress,
.result-summary,
.warning-stack,
.attempt-list,
.history-list {
  display: grid;
  gap: 18px;
}

.compression-signal-row,
.target-badges,
.action-row,
.attempt-item__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.panel-header {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
}

.mode-item {
  padding: 18px;
  border-radius: 24px;
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.mode-item h3,
.fact-column h3 {
  margin: 0;
  color: var(--text-strong);
  font-size: 1rem;
}

.mode-item__accents {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.file-input {
  display: none;
}

.compression-dropzone,
.result-preview,
.result-placeholder {
  min-height: 220px;
  border-radius: 28px;
  background: linear-gradient(150deg, rgba(252, 247, 239, 0.96), rgba(231, 220, 206, 0.92));
  box-shadow: var(--shadow-pressed);
}

.compression-dropzone {
  display: grid;
  gap: 12px;
  align-content: center;
  justify-items: start;
  padding: 24px;
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}

.compression-dropzone:hover,
.compression-dropzone--active {
  transform: translateY(-2px);
  box-shadow: var(--shadow-floating);
}

.compression-dropzone strong {
  color: var(--text-strong);
  font-size: 1.16rem;
}

.compression-dropzone__badge {
  display: inline-flex;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(29, 92, 85, 0.1);
  color: var(--accent-cool-strong);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.fact-grid,
.result-summary,
.fact-columns {
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.fact-chip,
.result-metric {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 22px;
  background: rgba(255, 252, 246, 0.74);
  box-shadow: var(--shadow-pressed);
}

.fact-chip span,
.result-metric span {
  color: var(--text-soft);
  font-size: 0.76rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.fact-chip strong,
.result-metric strong {
  color: var(--text-strong);
  font-size: 0.96rem;
}

.mode-toggle {
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.mode-toggle__button,
.history-item {
  display: grid;
  gap: 8px;
  padding: 18px;
  border: 0;
  border-radius: 24px;
  text-align: left;
  cursor: pointer;
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
  color: inherit;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}

.mode-toggle__button:hover,
.history-item:hover,
.mode-toggle__button--active,
.history-item--active {
  transform: translateY(-2px);
  box-shadow: var(--shadow-floating);
}

.mode-toggle__button strong,
.history-item strong {
  color: var(--text-strong);
}

.mode-toggle__button span,
.history-item p {
  color: var(--text-soft);
  font-size: 0.9rem;
}

.form-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.form-field {
  display: grid;
  gap: 10px;
}

.form-field span {
  color: var(--text-soft);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.form-field select,
.form-field input,
.compound-row code {
  min-height: 48px;
  padding: 12px 14px;
  border: 0;
  border-radius: 18px;
  background: rgba(255, 252, 246, 0.78);
  box-shadow: var(--shadow-pressed);
  color: var(--text-strong);
}

.compound-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.compound-row input[type='color'] {
  min-width: 56px;
  padding: 8px;
}

.range-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.range-row strong {
  color: var(--text-strong);
}

.job-progress {
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 252, 246, 0.72);
  box-shadow: var(--shadow-pressed);
}

.job-progress__labels {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-soft);
  font-size: 0.84rem;
  font-weight: 700;
}

.job-progress__track {
  position: relative;
  overflow: hidden;
  height: 12px;
  border-radius: 999px;
  background: rgba(16, 36, 38, 0.08);
}

.job-progress__bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent-coral), var(--accent-amber));
}

.status-note {
  margin: 0;
  color: var(--text-soft);
}

.status-note--error {
  color: #a34723;
}

.result-preview {
  display: grid;
  place-items: center;
  padding: 20px;
}

.result-preview img,
.result-preview__video {
  width: 100%;
  max-height: 360px;
  border-radius: 22px;
  object-fit: contain;
}

.result-preview__audio {
  width: 100%;
}

.warning-chip {
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(243, 138, 85, 0.12);
  color: #8a4624;
  box-shadow: var(--shadow-pressed);
}

.attempt-item {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(255, 252, 246, 0.74);
  box-shadow: var(--shadow-pressed);
}

.attempt-item strong {
  color: var(--text-strong);
}

.attempt-item__facts span {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(29, 92, 85, 0.08);
  color: var(--accent-cool-strong);
  font-size: 0.8rem;
  font-weight: 700;
}

.result-placeholder {
  display: grid;
  place-content: center;
  padding: 24px;
  text-align: center;
}

.result-placeholder--compact {
  min-height: 180px;
}

.history-list {
  gap: 14px;
}

.history-item {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

@media (max-width: 1180px) {
  .compression-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .compression-panel--result {
    grid-column: 1 / -1;
  }
}

@media (max-width: 880px) {
  .compression-hero-grid,
  .compression-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .panel-header {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
