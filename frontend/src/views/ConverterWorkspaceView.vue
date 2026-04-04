<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  converterAcceptAttribute,
  listConverterScenariosByFamily,
} from '../features/converter/domain/converter-registry'
import { useConverterWorkspace } from '../features/converter/composables/useConverterWorkspace'

const fileInput = ref<HTMLInputElement | null>(null)
const isDragActive = ref(false)

const imageScenarios = listConverterScenariosByFamily('image')
const documentScenarios = listConverterScenariosByFamily('document')

const {
  prepared,
  result,
  availablePresets,
  availableTargets,
  activeTarget,
  activePreset,
  isLoading,
  isConverting,
  errorMessage,
  selectedTargetExtension,
  selectedPresetId,
  quality,
  backgroundColor,
  selectFile,
  clearSelection,
  convert,
  downloadResult,
} = useConverterWorkspace()

const sourceFacts = computed(() => {
  if (!prepared.value) {
    return []
  }

  return [
    { label: 'Источник', value: prepared.value.file.name },
    {
      label: 'Размер',
      value: new Intl.NumberFormat('ru-RU').format(prepared.value.file.size) + ' bytes',
    },
    { label: 'Формат', value: prepared.value.source.label },
    { label: 'Pipeline', value: prepared.value.source.statusLabel },
  ]
})

const currentScenario = computed(() =>
  prepared.value?.scenarios.find(
    (scenario) => scenario.targetExtension === selectedTargetExtension.value,
  ),
)

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
          <p class="eyebrow">Jack of all trades</p>
          <p class="brand-lockup__title">Converter Workspace</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <RouterLink class="back-link" to="/">Back to Home</RouterLink>
        <span class="chip-pill">Image Conversion</span>
        <span class="chip-pill chip-pill--accent">Browser-first pipeline</span>
      </div>
    </header>

    <section class="converter-hero-grid">
      <article class="panel-surface converter-hero-copy">
        <p class="eyebrow">Iteration 03 · Converter</p>
        <h1>
          Первый проход конвертера уже умеет брать сложные image-source и собирать практичные
          target-форматы.
        </h1>
        <p class="lead">
          Модуль строится не как набор случайных кнопок, а как реестр сценариев поверх decode/encode
          pipeline: source-стратегия подготавливает единый raster contract, target-стратегия
          кодирует его в итоговый формат, а UI остаётся thin-слоем над runtime.
        </p>

        <div class="converter-signal-row">
          <span class="chip-pill">HEIC / TIFF / RAW decode</span>
          <span class="chip-pill">JPG / PNG / WebP / PDF targets</span>
          <span class="chip-pill">Preset-driven resize</span>
          <span class="chip-pill">Scenario registry</span>
          <span class="chip-pill">Shared imaging layer</span>
        </div>
      </article>

      <article class="panel-surface converter-system-card">
        <p class="eyebrow">Current Matrix</p>
        <h2>На старте закрыт browser-first набор частых image-конверсий.</h2>

        <div class="scenario-list" aria-label="Доступные сценарии конвертации">
          <article v-for="scenario in imageScenarios" :key="scenario.id" class="scenario-item">
            <div>
              <h3>{{ scenario.label }}</h3>
              <p>{{ scenario.notes }}</p>
            </div>
            <span class="chip-pill chip-pill--compact chip-pill--accent">{{
              scenario.statusLabel
            }}</span>
          </article>
        </div>

        <div class="scenario-list scenario-list--secondary" aria-label="Документные сценарии">
          <article v-for="scenario in documentScenarios" :key="scenario.id" class="scenario-item">
            <div>
              <h3>{{ scenario.label }}</h3>
              <p>{{ scenario.notes }}</p>
            </div>
            <span class="chip-pill chip-pill--compact">{{ scenario.statusLabel }}</span>
          </article>
        </div>
      </article>
    </section>

    <section class="converter-main-grid">
      <article class="panel-surface converter-panel">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Source Intake</p>
            <h2>Выбери изображение и подходящий target.</h2>
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
          <span class="converter-dropzone__badge">Drop / Select</span>
          <strong>Загрузи `jpg`, `png`, `webp`, `bmp`, `svg`, `heic`, `tiff` или `raw`.</strong>
          <span>
            Конвертер сам определит, какой decode-path нужен: нативный browser raster либо
            heavy-format adapter, а затем соберёт image либо PDF target.
          </span>
        </button>

        <p v-if="errorMessage" class="status-message status-message--error">{{ errorMessage }}</p>
        <p v-else-if="isLoading" class="status-message">
          Подготавливаю source-сценарии для выбранного файла...
        </p>
        <p v-else-if="isConverting" class="status-message">
          Собираю итоговый target через encode pipeline...
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
              <p class="control-label">Target format</p>
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
                  <small>{{ target.statusLabel }}</small>
                </button>
              </div>
            </div>

            <div>
              <p class="control-label">Preset profile</p>
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
                  <small>{{ preset.statusLabel }}</small>
                </button>
              </div>
            </div>

            <div v-if="currentScenario" class="scenario-callout">
              <p class="control-label">Активный сценарий</p>
              <h3>{{ currentScenario.label }}</h3>
              <p>{{ currentScenario.notes }}</p>
            </div>

            <div v-if="activePreset" class="scenario-callout">
              <p class="control-label">Активный пресет</p>
              <h3>{{ activePreset.label }}</h3>
              <p>{{ activePreset.detail }}</p>
            </div>

            <label v-if="activeTarget?.supportsQuality" class="form-field">
              <span class="control-label">Quality</span>
              <div class="range-row">
                <input v-model="quality" type="range" min="0.55" max="1" step="0.01" />
                <strong>{{ Math.round(quality * 100) }}%</strong>
              </div>
            </label>

            <label v-if="activeTarget && !activeTarget.supportsTransparency" class="form-field">
              <span class="control-label">Background for alpha</span>
              <div class="color-row">
                <input v-model="backgroundColor" type="color" />
                <span>{{ backgroundColor }}</span>
              </div>
            </label>
          </div>

          <button
            type="button"
            class="action-button action-button--accent action-button--wide"
            :disabled="isConverting || !selectedTargetExtension"
            @click="convert"
          >
            Convert to {{ activeTarget?.label ?? 'target' }}
          </button>
        </div>
      </article>

      <article class="panel-surface converter-panel converter-panel--result">
        <div class="panel-header">
          <div>
            <p class="eyebrow">Result Deck</p>
            <h2>Предпросмотр итогового файла и быстрый download.</h2>
          </div>

          <button
            v-if="result"
            type="button"
            class="action-button action-button--accent"
            @click="downloadResult"
          >
            Download
          </button>
        </div>

        <div v-if="result" class="result-stack">
          <div class="result-preview">
            <img
              v-if="result.kind === 'image'"
              :src="result.objectUrl"
              :alt="`Converted preview ${result.fileName}`"
            />
            <iframe
              v-else
              class="result-preview__frame"
              :src="result.objectUrl"
              title="PDF preview"
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
              <strong>{{ result.kind === 'document' ? 'Document output' : 'Image output' }}</strong>
            </article>
            <article class="fact-card">
              <span>Preset</span>
              <strong>{{ result.preset.label }}</strong>
            </article>
            <article class="fact-card">
              <span>Размерность</span>
              <strong>{{ result.width }} x {{ result.height }}</strong>
            </article>
            <article class="fact-card">
              <span>Источник</span>
              <strong>{{ result.sourceWidth }} x {{ result.sourceHeight }}</strong>
            </article>
            <article class="fact-card">
              <span>Blob size</span>
              <strong>{{ new Intl.NumberFormat('ru-RU').format(result.blob.size) }} bytes</strong>
            </article>
          </div>

          <p
            v-for="warning in result.warnings"
            :key="warning"
            class="status-message status-message--warning"
          >
            {{ warning }}
          </p>
        </div>

        <div v-else class="result-placeholder">
          <p class="eyebrow">No output yet</p>
          <h3>Сначала выбери source и запусти конвертацию.</h3>
          <p>После первого успешного encode здесь появится итоговый preview и кнопка скачивания.</p>
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
.scenario-callout h3 {
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
.scenario-callout h3 {
  font-size: clamp(1.7rem, 2.2vw, 2.45rem);
  line-height: 1;
}

.lead,
.scenario-item p,
.scenario-callout p,
.result-placeholder p {
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
.result-facts {
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
.control-cluster {
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

.form-field {
  display: grid;
  gap: 12px;
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.22);
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

.result-placeholder {
  display: grid;
  gap: 14px;
  place-items: start;
  min-height: 420px;
  padding: 28px;
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
  .scenario-item {
    flex-direction: column;
  }

  .facts-grid {
    grid-template-columns: 1fr;
  }

  .target-grid,
  .preset-grid,
  .result-facts,
  .converter-signal-row {
    flex-direction: column;
  }
}
</style>
