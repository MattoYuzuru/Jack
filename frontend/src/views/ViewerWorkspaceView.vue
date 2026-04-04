<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listViewerFormatsByFamily,
  viewerAcceptAttribute,
} from '../features/viewer/domain/viewer-registry'
import { useViewerWorkspace } from '../features/viewer/composables/useViewerWorkspace'
import { useViewerImageTools } from '../features/viewer/composables/useViewerImageTools'
import {
  createEmptyEditableMetadata,
  type ViewerEditableMetadata,
} from '../features/viewer/application/viewer-metadata'
import {
  canEmbedMetadata,
  exportViewerMetadata,
} from '../features/viewer/application/viewer-metadata-writer'

const fileInput = ref<HTMLInputElement | null>(null)
const previewStage = ref<HTMLElement | null>(null)
const previewImage = ref<HTMLImageElement | null>(null)
const isDragActive = ref(false)
const isFullscreen = ref(false)
const metadataQuery = ref('')
const metadataDraft = ref<ViewerEditableMetadata>(createEmptyEditableMetadata())
const isSavingMetadata = ref(false)
const metadataSaveMessage = ref('')

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

watch(
  () => (selection.value?.kind === 'image' ? selection.value.metadata.editable : null),
  (editableMetadata) => {
    metadataDraft.value = editableMetadata
      ? { ...editableMetadata }
      : createEmptyEditableMetadata()
    metadataSaveMessage.value = ''
    metadataQuery.value = ''
  },
  { immediate: true },
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
        <span class="chip-pill">Image Tools</span>
        <span class="chip-pill chip-pill--accent">Metadata Live</span>
      </div>
    </header>

    <section class="viewer-hero-grid">
      <article class="panel-surface viewer-intro">
        <p class="eyebrow">Iteration 02 · Image Viewer</p>
        <h1>Viewer уже не только показывает файл, но и даёт нормальный image workbench.</h1>
        <p class="lead">
          Поверх registry и decode-стратегий теперь сидят color lab, EXIF/ICC inspector, metadata
          export и быстрые инструменты для анализа прямо внутри viewer-потока.
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
            <span>Drag and drop или ручной выбор. Фокус этого прохода на image analysis tooling.</span>
          </div>

          <div class="viewer-dropzone__actions">
            <button
              class="action-button action-button--accent"
              type="button"
              @click="openFilePicker"
            >
              Pick File
            </button>
            <button class="action-button" type="button" :disabled="!selection" @click="clearSelection">
              Clear
            </button>
          </div>
        </div>

        <div class="signal-row">
          <span class="chip-pill">Zoom + rotate</span>
          <span class="chip-pill">Fullscreen</span>
          <span class="chip-pill">Color picker + loupe</span>
          <span class="chip-pill">Histogram + swatches</span>
          <span class="chip-pill">EXIF + ICC inspector</span>
        </div>
      </article>

      <article class="panel-surface viewer-stage-card">
        <div class="viewer-stage-card__header">
          <div>
            <p class="eyebrow">Preview Stage</p>
            <h2>{{ selection?.file.name ?? 'Выбери изображение для просмотра' }}</h2>
          </div>

          <div class="viewer-toolbar">
            <button class="icon-button" type="button" :disabled="selection?.kind !== 'image'" @click="zoomOut">
              -
            </button>
            <button class="icon-button" type="button" :disabled="selection?.kind !== 'image'" @click="zoomIn">
              +
            </button>
            <button class="icon-button" type="button" :disabled="selection?.kind !== 'image'" @click="rotateLeft">
              Left
            </button>
            <button class="icon-button" type="button" :disabled="selection?.kind !== 'image'" @click="rotateRight">
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
          :class="{ 'viewer-stage--checker': isTransparencyGridVisible }"
        >
          <div v-if="isLoading" class="viewer-empty-state">
            <strong>Подготавливаю preview...</strong>
            <span>
              Runtime определяет формат, decode-стратегию, metadata payload и размерность
              изображения.
            </span>
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
              Сейчас закрыт весь image-format set из roadmap: `jpg`, `jpeg`, `png`, `webp`,
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

        <p
          v-if="selection?.kind === 'image' && !filteredMetadataGroups.length"
          class="viewer-panel__empty"
        >
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
              :disabled="selection?.kind !== 'image'"
            ></textarea>
          </label>

          <label>
            <span>Artist</span>
            <input
              v-model="metadataDraft.artist"
              type="text"
              placeholder="Author / photographer"
              :disabled="selection?.kind !== 'image'"
            />
          </label>

          <label>
            <span>Copyright</span>
            <input
              v-model="metadataDraft.copyright"
              type="text"
              placeholder="Copyright notice"
              :disabled="selection?.kind !== 'image'"
            />
          </label>

          <label>
            <span>Captured at</span>
            <input
              v-model="metadataDraft.capturedAt"
              type="datetime-local"
              :disabled="selection?.kind !== 'image'"
            />
          </label>

          <div class="metadata-editor__footer">
            <p class="metadata-editor__mode">
              {{
                metadataEmbeddingAvailable
                  ? 'Для JPEG viewer соберёт новый файл с обновлёнными EXIF-полями.'
                  : 'Для этого формата viewer соберёт sidecar JSON с metadata patch.'
              }}
            </p>
            <button
              class="action-button action-button--accent"
              type="submit"
              :disabled="selection?.kind !== 'image' || isSavingMetadata"
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
              <p v-if="format.aliases.length" class="format-card__extensions">
                Extensions: .{{ format.extension }}, .{{ format.aliases.join(',.') }}
              </p>
            </article>
          </div>

          <div class="format-grid">
            <article
              v-for="format in decodeFormats"
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
              <p v-if="format.aliases.length" class="format-card__extensions">
                Extensions: .{{ format.extension }}, .{{ format.aliases.join(',.') }}
              </p>
            </article>
          </div>
        </div>
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Architecture</p>
        <h2>Viewer теперь объединяет preview, metadata и image-tools в один runtime.</h2>
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
            <strong>Metadata Payload</strong>
            <p>Держит summary, grouped inspector, editable draft и embedded thumbnail в одном контракте.</p>
          </article>
          <article class="architecture-card">
            <strong>Workspace State</strong>
            <p>Управляет transform-состоянием viewport и жизненным циклом object URL без утечек.</p>
          </article>
          <article class="architecture-card">
            <strong>Color Lab</strong>
            <p>Поверх image model строит pixel picker, loupe, swatches и histogram через offscreen canvas.</p>
          </article>
          <article class="architecture-card">
            <strong>Metadata Export</strong>
            <p>JPEG пишет common EXIF в новый файл, а остальные форматы отдают sidecar JSON patch.</p>
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
.format-card__extensions,
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

.viewer-stage--checker {
  background:
    linear-gradient(45deg, rgba(29, 92, 85, 0.08) 25%, transparent 25%),
    linear-gradient(-45deg, rgba(29, 92, 85, 0.08) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, rgba(255, 203, 148, 0.16) 75%),
    linear-gradient(-45deg, transparent 75%, rgba(255, 203, 148, 0.16) 75%),
    linear-gradient(155deg, rgba(255, 251, 245, 0.8), rgba(227, 216, 201, 0.86));
  background-size: 24px 24px, 24px 24px, 24px 24px, 24px 24px, auto;
  background-position:
    0 0,
    0 12px,
    12px -12px,
    -12px 0,
    0 0;
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

.color-lab,
.metadata-group-stack,
.metadata-editor,
.format-grid,
.architecture-grid {
  display: grid;
  gap: 14px;
}

.color-lab__hero,
.color-lab__loupe-row,
.metadata-group__header,
.format-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.color-lab__hero {
  align-items: stretch;
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

.color-lab__values strong,
.format-card__meta strong,
.architecture-card strong,
.metadata-group__header strong {
  color: var(--text-strong);
  font-size: 1.02rem;
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

.metadata-editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
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
  .metadata-group__header,
  .format-card__meta,
  .metadata-editor__footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .viewer-panel {
    grid-column: span 12;
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

  .viewer-image-frame {
    min-height: 340px;
  }

  .color-lab__swatch,
  .color-lab__loupe {
    width: 100%;
    min-width: 0;
  }

  .histogram-panel__chart {
    gap: 4px;
  }
}
</style>
