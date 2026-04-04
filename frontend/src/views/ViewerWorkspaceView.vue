<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listViewerFormatsByFamily,
  viewerAcceptAttribute,
} from '../features/viewer/domain/viewer-registry'
import { useViewerWorkspace } from '../features/viewer/composables/useViewerWorkspace'

const fileInput = ref<HTMLInputElement | null>(null)
const previewStage = ref<HTMLElement | null>(null)
const isDragActive = ref(false)
const isFullscreen = ref(false)

const imageFormats = listViewerFormatsByFamily('image')

const browserNativeFormats = computed(() =>
  imageFormats.filter((definition) => definition.previewPipeline === 'browser-native'),
)

const decodeFormats = computed(() =>
  imageFormats.filter((definition) => definition.previewPipeline === 'client-decode'),
)

const {
  selection,
  isLoading,
  errorMessage,
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
    items.push(...selection.value.metadata)
  }

  return items
})

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

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreenState)
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
        <span class="chip-pill">Image first</span>
        <span class="chip-pill chip-pill--accent">Registry live</span>
      </div>
    </header>

    <section class="viewer-hero-grid">
      <article class="panel-surface viewer-intro">
        <p class="eyebrow">Iteration 02 · Image Viewer</p>
        <h1>Viewer уже закрывает весь первый image-format set, а не только browser-native слой.</h1>
        <p class="lead">
          Архитектура построена от registry и decode-стратегий: быстрые форматы идут напрямую через
          браузер, а `heic`, `tiff` и `raw` проходят через отдельные client-side adapters без
          развала общего viewer-потока.
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

          <div class="viewer-dropzone__copy">
            <strong>Загрузить файл в viewer</strong>
            <span
              >Drag and drop или ручной выбор. На первом проходе фокус на image-first preview.</span
            >
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
          <span class="chip-pill">Zoom + rotate</span>
          <span class="chip-pill">Fullscreen</span>
          <span class="chip-pill">Format capability map</span>
        </div>
      </article>

      <article class="panel-surface viewer-stage-card">
        <div class="viewer-stage-card__header">
          <div>
            <p class="eyebrow">Preview Stage</p>
            <h2>
              {{ selection?.file.name ?? 'Выбери изображение для просмотра' }}
            </h2>
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
              :disabled="selection?.kind !== 'image'"
              @click="toggleFullscreen"
            >
              {{ isFullscreen ? 'Exit FS' : 'Fullscreen' }}
            </button>
          </div>
        </div>

        <div ref="previewStage" class="viewer-stage">
          <div v-if="isLoading" class="viewer-empty-state">
            <strong>Подготавливаю preview...</strong>
            <span
              >Runtime определяет формат, decode-стратегию и, если нужно, читает размерность
              изображения.</span
            >
          </div>

          <div v-else-if="errorMessage" class="viewer-empty-state viewer-empty-state--warning">
            <strong>Preview не собран</strong>
            <span>{{ errorMessage }}</span>
          </div>

          <div v-else-if="selection?.kind === 'image'" class="viewer-image-frame">
            <img
              class="viewer-image-frame__image"
              :src="selection.objectUrl"
              :alt="selection.file.name"
              :style="{ transform: viewportTransform }"
            />
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
              На этом проходе заведены все image-форматы из roadmap: `jpg`, `jpeg`, `png`, `webp`,
              `avif`, `heic`, `gif`, `bmp`, `tiff`, `svg`, `raw`, `ico`.
            </span>
          </div>
        </div>

        <div class="viewer-stage-card__footer">
          <span class="chip-pill chip-pill--compact">Zoom: {{ zoom.toFixed(1) }}x</span>
          <span class="chip-pill chip-pill--compact">Rotation: {{ rotation }}deg</span>
          <span
            v-if="selection?.kind === 'image'"
            class="chip-pill chip-pill--compact chip-pill--accent"
          >
            {{ selection.previewLabel }}
          </span>
        </div>
      </article>
    </section>

    <section class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Metadata</p>
        <h2>Факты по текущему файлу</h2>
        <dl class="facts-grid">
          <template v-for="fact in selectionFacts" :key="fact.label">
            <dt>{{ fact.label }}</dt>
            <dd>{{ fact.value }}</dd>
          </template>
        </dl>
        <p v-if="!selectionFacts.length" class="viewer-panel__empty">
          После выбора файла сюда подтянутся базовые metadata и текущий preview path.
        </p>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Capability Map</p>
        <h2>Форматы, которые уже заведены в image viewer</h2>
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
            <p v-if="format.aliases.length" class="format-card__extensions">
              Extensions: .{{ format.extension }}, .{{ format.aliases.join(',.') }}
            </p>
            <div class="format-card__tags">
              <span
                v-for="accent in format.accents"
                :key="accent"
                class="chip-pill chip-pill--compact"
              >
                {{ accent }}
              </span>
            </div>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Decode Adapters</p>
        <h2>Тяжёлые форматы уже проходят через отдельные client-side adapters</h2>
        <div class="format-grid">
          <article
            v-for="format in decodeFormats"
            :key="format.extension"
            class="format-card format-card--pipeline"
          >
            <div class="format-card__meta">
              <strong>{{ format.label }}</strong>
              <span class="chip-pill chip-pill--compact chip-pill--accent">{{
                format.statusLabel
              }}</span>
            </div>
            <p>{{ format.notes }}</p>
            <p v-if="format.aliases.length" class="format-card__extensions">
              Extensions: .{{ format.extension }}, .{{ format.aliases.join(',.') }}
            </p>
            <div class="format-card__tags">
              <span
                v-for="accent in format.accents"
                :key="accent"
                class="chip-pill chip-pill--compact"
              >
                {{ accent }}
              </span>
            </div>
          </article>
        </div>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Architecture</p>
        <h2>Viewer строится как расширяемый модуль, а не как набор `if/else`.</h2>
        <div class="architecture-grid">
          <article class="architecture-card">
            <strong>Format Registry</strong>
            <p>Описывает extension, MIME, preview pipeline и capability-статус в одном месте.</p>
          </article>
          <article class="architecture-card">
            <strong>Strategy Resolver</strong>
            <p>
              Назначает формату конкретный runtime-path: browser-native preview или client-side
              decode adapter.
            </p>
          </article>
          <article class="architecture-card">
            <strong>Workspace State</strong>
            <p>Управляет transform-состоянием viewport и жизненным циклом object URL без утечек.</p>
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

.viewer-dropzone__copy span {
  color: var(--text-soft);
}

.viewer-dropzone__actions,
.signal-row,
.viewer-toolbar,
.viewer-stage-card__footer,
.format-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.viewer-stage-card {
  display: grid;
  gap: 20px;
}

.viewer-stage-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.viewer-image-frame {
  display: grid;
  width: 100%;
  min-height: 480px;
  place-items: center;
  overflow: auto;
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

.viewer-empty-state--pipeline strong {
  color: var(--accent-cool-strong);
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
}

.viewer-panel--wide {
  grid-column: span 12;
}

.facts-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1fr);
  gap: 12px 18px;
  margin: 0;
}

.facts-grid dt,
.facts-grid dd {
  margin: 0;
  padding: 12px 14px;
  border-radius: 18px;
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.facts-grid dt {
  color: var(--text-main);
  font-weight: 700;
}

.facts-grid dd,
.viewer-panel__empty,
.format-card__extensions,
.format-card p,
.architecture-card p {
  color: var(--text-soft);
}

.format-grid,
.architecture-grid {
  display: grid;
  gap: 14px;
}

.format-card,
.architecture-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-pressed);
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

.format-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.format-card__meta strong,
.architecture-card strong {
  color: var(--text-strong);
  font-size: 1.02rem;
}

.architecture-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.architecture-card {
  background: var(--surface-muted);
}

@media (max-width: 1180px) {
  .viewer-hero-grid {
    grid-template-columns: 1fr;
  }

  .viewer-panel {
    grid-column: span 6;
  }
}

@media (max-width: 860px) {
  .viewer-stage-card__header,
  .format-card__meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .viewer-panel {
    grid-column: span 12;
  }

  .architecture-grid {
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

  .viewer-image-frame {
    min-height: 340px;
  }

  .facts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
