<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useCompressionWorkspace } from '../features/compression/composables/useCompressionWorkspace'

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
  QUEUED: 'Queued',
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
}

const currentStatusLabel = computed(() =>
  activeJobStatus.value ? statusLabels[activeJobStatus.value] ?? activeJobStatus.value : 'Idle',
)
const progressWidth = computed(() => `${Math.max(0, Math.min(activeJobProgressPercent.value, 100))}%`)
const historyEntries = computed(() => resultHistory.value.slice(0, 5))
const sourceFacts = computed(() => {
  if (!prepared.value) {
    return []
  }

  return [
    { label: 'Source', value: prepared.value.file.name },
    { label: 'Family', value: prepared.value.source.family.toUpperCase() },
    { label: 'Size', value: formatBytes(prepared.value.file.size) },
    { label: 'Strategy', value: prepared.value.source.statusLabel },
  ]
})

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
          <p class="eyebrow">Jack of all trades</p>
          <p class="brand-lockup__title">Compression Workspace</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <RouterLink class="back-link" to="/">Back to Home</RouterLink>
        <span class="chip-pill">Iteration 04</span>
        <span class="chip-pill chip-pill--accent">Size-first route</span>
      </div>
    </header>

    <section class="compression-hero-grid">
      <article class="panel-surface compression-hero-copy">
        <p class="eyebrow">Iteration 04 · Compression</p>
        <h1>
          Compression теперь живёт как отдельный backend-first маршрут: пользователь задаёт size
          goal, а сервер сам выбирает candidate ladder и лучший итоговый artifact.
        </h1>
        <p class="lead">
          Этот экран не дублирует converter. Здесь продуктовая задача другая: не выбрать формат
          руками, а получить максимально компактный или целевой по размеру файл для image, video и
          audio групп. Backend route `FILE_COMPRESS` reuse'ит существующие `IMAGE_CONVERT` и
          `MEDIA_CONVERT`, но наружу отдаёт уже единый compression manifest, финальный result и
          preview.
        </p>

        <div class="compression-signal-row">
          <span class="chip-pill">Image compression</span>
          <span class="chip-pill">Video bitrate targeting</span>
          <span class="chip-pill">Audio delivery shrink</span>
          <span class="chip-pill">Maximum reduction</span>
          <span class="chip-pill">Target size</span>
          <span class="chip-pill">Custom quality limits</span>
          <span class="chip-pill">Single result contract</span>
          <span class="chip-pill">Artifact history</span>
        </div>
      </article>

      <article class="panel-surface compression-system-card">
        <p class="eyebrow">Route Shape</p>
        <h2>Compression решает размер, а не формальный target conversion.</h2>

        <div class="mode-list">
          <article v-for="mode in availableModes" :key="mode.id" class="mode-item">
            <div>
              <h3>{{ mode.label }}</h3>
              <p>{{ mode.detail }}</p>
            </div>
            <div class="mode-item__accents">
              <span v-for="accent in mode.accents" :key="accent" class="chip-pill chip-pill--compact">
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
            <p class="eyebrow">Source</p>
            <h2>Файл и входная family</h2>
          </div>
          <button type="button" class="action-button" @click="openFilePicker">
            Select file
          </button>
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
          <span class="compression-dropzone__badge">Drop / Select</span>
          <strong>{{ prepared ? prepared.file.name : 'Перетащи image, video или audio файл' }}</strong>
          <span>
            {{
              prepared
                ? prepared.source.notes
                : 'Compression matrix уже знает, какие sources поддерживаются и какие targets доступны для каждой family.'
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
            <p class="eyebrow">Controls</p>
            <h2>Режим и size budget</h2>
          </div>
          <button type="button" class="action-button" :disabled="!prepared" @click="clearSelection">
            Reset
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
            <strong>{{ mode.label }}</strong>
            <span>{{ mode.detail }}</span>
          </button>
        </div>

        <div class="form-grid">
          <label v-if="prepared && activeMode?.supportsTargetSelection" class="form-field">
            <span>Preferred target</span>
            <select v-model="selectedTargetExtension">
              <option v-for="option in targetOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showTargetSizeControl" class="form-field">
            <span>Target size</span>
            <div class="compound-row">
              <input v-model="targetSizeValue" type="number" min="0.1" step="0.1" placeholder="5" />
              <select v-model="targetSizeUnit">
                <option value="MB">MB</option>
                <option value="KB">KB</option>
              </select>
            </div>
          </label>

          <label v-if="showResolutionControl" class="form-field">
            <span>Resolution limit</span>
            <select v-model="selectedResolution">
              <option v-for="option in resolutionOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showFpsControl" class="form-field">
            <span>Target FPS</span>
            <select v-model="selectedTargetFps">
              <option v-for="option in fpsOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showVideoBitrateControl" class="form-field">
            <span>Video bitrate</span>
            <select v-model="selectedVideoBitrateKbps">
              <option v-for="option in videoBitrateOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showAudioBitrateControl" class="form-field">
            <span>Audio bitrate</span>
            <select v-model="selectedAudioBitrateKbps">
              <option v-for="option in audioBitrateOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label v-if="showQualityControl" class="form-field">
            <span>Quality</span>
            <div class="range-row">
              <input v-model.number="quality" type="range" min="0.45" max="1" step="0.01" />
              <strong>{{ Math.round(quality * 100) }}%</strong>
            </div>
          </label>

          <label v-if="showBackgroundColorControl" class="form-field">
            <span>Background fill</span>
            <div class="compound-row">
              <input v-model="backgroundColor" type="color" />
              <code>{{ backgroundColor }}</code>
            </div>
          </label>
        </div>

        <div class="job-progress">
          <div class="job-progress__labels">
            <span>Status: {{ currentStatusLabel }}</span>
            <span>{{ activeJobProgressPercent }}%</span>
          </div>
          <div class="job-progress__track">
            <div class="job-progress__bar" :style="{ width: progressWidth }"></div>
          </div>
          <p v-if="processingMessage">{{ processingMessage }}</p>
          <p v-else-if="activeJobId">Job: {{ activeJobId }}</p>
        </div>

        <div class="action-row">
          <button
            type="button"
            class="action-button action-button--accent"
            :disabled="!prepared || isLoading || isCompressing"
            @click="compress"
          >
            {{ isCompressing ? 'Compressing...' : 'Run compression' }}
          </button>
          <button
            type="button"
            class="action-button"
            :disabled="!canRetry"
            @click="retryLastCompression"
          >
            Retry
          </button>
          <button
            type="button"
            class="action-button"
            :disabled="!activeJobId || isCancelling || !isCompressing"
            @click="cancelCompression"
          >
            {{ isCancelling ? 'Cancelling...' : 'Cancel' }}
          </button>
        </div>

        <p v-if="errorMessage" class="status-note status-note--error">{{ errorMessage }}</p>
        <p v-else-if="activeTarget" class="status-note">
          Active target:
          <strong>{{ activeTarget.label }}</strong>
          · {{ activeTarget.notes }}
        </p>
      </article>

      <article class="panel-surface compression-panel compression-panel--result">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Result</p>
            <h2>Preview, reduction и warnings</h2>
          </div>
          <button
            type="button"
            class="action-button action-button--accent"
            :disabled="!result"
            @click="downloadResult()"
          >
            Download result
          </button>
        </div>

        <div v-if="result" class="result-stack">
          <div class="result-preview">
            <img
              v-if="result.previewKind === 'image'"
              :src="result.previewObjectUrl"
              :alt="`Compression preview ${result.fileName}`"
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
              <span>Result</span>
              <strong>{{ result.fileName }}</strong>
            </article>
            <article class="result-metric">
              <span>Mode</span>
              <strong>{{ result.mode }}</strong>
            </article>
            <article class="result-metric">
              <span>Source size</span>
              <strong>{{ formatBytes(result.sourceSizeBytes) }}</strong>
            </article>
            <article class="result-metric">
              <span>Result size</span>
              <strong>{{ formatBytes(result.resultSizeBytes) }}</strong>
            </article>
            <article class="result-metric">
              <span>Reduction</span>
              <strong>{{ result.reductionPercent.toFixed(1) }}%</strong>
            </article>
            <article class="result-metric">
              <span>Target status</span>
              <strong>{{ result.targetMet ? 'Reached' : 'Best effort' }}</strong>
            </article>
            <article class="result-metric">
              <span>Backend job</span>
              <strong>{{ result.backendJobId }}</strong>
            </article>
            <article class="result-metric">
              <span>Completed</span>
              <strong>{{ formatDate(result.createdAt) }}</strong>
            </article>
          </div>

          <div class="fact-columns">
            <div class="fact-column">
              <h3>Compression facts</h3>
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
              <h3>Source facts</h3>
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
              <h3>Result facts</h3>
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
                <p>{{ attempt.runtimeLabel }}</p>
              </div>
              <div class="attempt-item__facts">
                <span>{{ attempt.targetExtension.toUpperCase() }}</span>
                <span>{{ formatBytes(attempt.resultSizeBytes) }}</span>
                <span>{{ attempt.targetMet ? 'Hit target' : 'Candidate' }}</span>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="result-placeholder">
          <h3>Compression result пока не собран.</h3>
          <p>
            Выбери файл, укажи режим и запусти compression. После этого backend вернёт не только
            final artifact, но и attempt ladder, чтобы было видно, как route пришёл к результату.
          </p>
        </div>
      </article>

      <article class="panel-surface compression-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">History</p>
            <h2>Последние compression runs</h2>
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
          <p>Каждый успешный compression run остаётся здесь как быстрый повторный reference.</p>
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
