<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listViewerFormatsByFamily,
  viewerAcceptAttribute,
} from '../features/viewer/domain/viewer-registry'
import { useViewerWorkspace } from '../features/viewer/composables/useViewerWorkspace'
import { useViewerImageTools } from '../features/viewer/composables/useViewerImageTools'
import { findViewerDocumentMatches } from '../features/viewer/application/viewer-document'
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
const documentQuery = ref('')
const documentSheetIndex = ref(0)
const metadataDraft = ref<ViewerEditableMetadata>(createEmptyEditableMetadata())
const isSavingMetadata = ref(false)
const metadataSaveMessage = ref('')

const imageFormats = listViewerFormatsByFamily('image')
const documentFormats = listViewerFormatsByFamily('document')

const browserNativeFormats = computed(() =>
  imageFormats.filter((definition) => definition.previewPipeline === 'browser-native'),
)

const decodeFormats = computed(() =>
  imageFormats.filter((definition) => definition.previewPipeline === 'client-decode'),
)

const activeDocumentFormats = computed(() =>
  documentFormats.filter((definition) => definition.previewPipeline !== 'planned'),
)

const plannedDocumentFormats = computed(() =>
  documentFormats.filter((definition) => definition.previewPipeline === 'planned'),
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
    metadataDraft.value = editableMetadata ? { ...editableMetadata } : createEmptyEditableMetadata()
    metadataSaveMessage.value = ''
    metadataQuery.value = ''
  },
  { immediate: true },
)

watch(
  () => selection.value?.file.name,
  () => {
    documentQuery.value = ''
    documentSheetIndex.value = 0
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

const documentWarnings = computed(() =>
  selection.value?.kind === 'document' ? selection.value.warnings : [],
)

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
        <span class="chip-pill">Images + Docs</span>
        <span class="chip-pill chip-pill--accent">Document Foundation</span>
      </div>
    </header>

    <section class="viewer-hero-grid">
      <article class="panel-surface viewer-intro">
        <p class="eyebrow">Iteration 02 · File Viewer</p>
        <h1>Viewer теперь идёт дальше image-only слоя и получает document runtime.</h1>
        <p class="lead">
          Архитектура остаётся registry-driven: image и document форматы идут через общий workspace,
          а конкретный preview выбирается по strategy contract без развала UI на отдельные экраны.
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
            <span>
              На этом проходе viewer уже умеет работать не только с изображениями, но и с первыми
              document-сценариями: `pdf`, `txt`, `csv`, `html`, `rtf`.
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
            <button class="action-button" type="button" :disabled="!selection" @click="clearSelection">
              Clear
            </button>
          </div>
        </div>

        <div class="signal-row">
          <span class="chip-pill">Image tooling live</span>
          <span class="chip-pill">PDF + text foundation</span>
          <span class="chip-pill">Search layer</span>
          <span class="chip-pill">Table + outline previews</span>
        </div>
      </article>

      <article class="panel-surface viewer-stage-card">
        <div class="viewer-stage-card__header">
          <div>
            <p class="eyebrow">Preview Stage</p>
            <h2>{{ selection?.file.name ?? 'Выбери файл для просмотра' }}</h2>
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
            <button class="icon-button" type="button" :disabled="!selection" @click="toggleFullscreen">
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
          :class="{ 'viewer-stage--checker': selection?.kind === 'image' && isTransparencyGridVisible }"
        >
          <div v-if="isLoading" class="viewer-empty-state">
            <strong>Подготавливаю preview...</strong>
            <span>
              Runtime определяет family, preview strategy и поднимает единый selection contract для
              image или document workspace.
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

          <div v-else-if="selection?.kind === 'document'" class="viewer-document-frame">
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
                  @click="documentSheetIndex = sheetIndex"
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

            <div v-else-if="selection.layout.mode === 'slides'" class="document-slide-grid">
              <article
                v-for="(slide, slideIndex) in selection.layout.slides"
                :key="slide.id"
                class="document-slide-card"
              >
                <div class="document-slide-card__meta">
                  <span class="chip-pill chip-pill--compact">Slide {{ slideIndex + 1 }}</span>
                </div>
                <h3>{{ slide.title }}</h3>
                <ul v-if="slide.bullets.length" class="document-slide-card__list">
                  <li v-for="bullet in slide.bullets" :key="bullet">{{ bullet }}</li>
                </ul>
                <p v-else class="viewer-panel__empty">На слайде не найден текстовый слой кроме заголовка.</p>
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
              Сейчас уже работают image formats и document preview paths: `pdf`, `txt`, `csv`,
              `html`, `rtf`, `docx`, `xlsx`, `pptx`.
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
            v-if="selection?.kind === 'image' || selection?.kind === 'document'"
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
            <input
              v-model="metadataDraft.artist"
              type="text"
              placeholder="Author / photographer"
            />
          </label>

          <label>
            <span>Copyright</span>
            <input
              v-model="metadataDraft.copyright"
              type="text"
              placeholder="Copyright notice"
            />
          </label>

          <label>
            <span>Captured at</span>
            <input v-model="metadataDraft.capturedAt" type="datetime-local" />
          </label>

          <div class="metadata-editor__footer">
            <p class="metadata-editor__mode">
              {{
                metadataEmbeddingAvailable
                  ? 'Для JPEG viewer соберёт новый файл с обновлёнными EXIF-полями.'
                  : 'Для этого формата viewer соберёт sidecar JSON с metadata patch.'
              }}
            </p>
            <button class="action-button action-button--accent" type="submit" :disabled="isSavingMetadata">
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
            </article>
          </div>
        </div>
      </article>
    </section>

    <section v-else-if="selection?.kind === 'document'" class="viewer-detail-grid">
      <article class="panel-surface viewer-panel">
        <p class="eyebrow">Document Summary</p>
        <h2>Статистика, warnings и preview facts</h2>
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
          <input v-model="documentQuery" type="text" placeholder="contract, total, heading..." />
        </label>
        <div v-if="documentMatches.length" class="search-match-stack">
          <article v-for="match in documentMatches" :key="match.id" class="search-match-card">
            {{ match.excerpt }}
          </article>
        </div>
        <p v-else class="viewer-panel__empty">
          {{
            documentQuery
              ? 'Совпадения не найдены.'
              : 'Search panel работает для PDF text layer, TXT, CSV, HTML, RTF и OOXML document adapters.'
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
            @click="documentSheetIndex = sheetIndex"
          >
            <strong>Sheet</strong>
            <span>{{ sheet.name }} · {{ sheet.table.totalRows }} rows</span>
          </article>
        </div>

        <div v-else-if="documentSlides.length" class="outline-stack">
          <article v-for="(slide, slideIndex) in documentSlides" :key="slide.id" class="outline-card">
            <strong>S{{ slideIndex + 1 }}</strong>
            <span>{{ slide.title }}</span>
          </article>
        </div>

        <div v-else-if="documentParagraphs.length" class="excerpt-stack">
          <article v-for="(paragraph, index) in documentParagraphs" :key="index" class="excerpt-card">
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
      </article>

      <article class="panel-surface viewer-panel viewer-panel--wide">
        <p class="eyebrow">Architecture</p>
        <h2>Document viewer строится поверх того же workspace, а не рядом с ним.</h2>
        <div class="architecture-grid">
          <article class="architecture-card">
            <strong>Format Registry</strong>
            <p>Теперь знает не только image family, но и document capability map с честными planned слотами.</p>
          </article>
          <article class="architecture-card">
            <strong>Strategy Runtime</strong>
            <p>Каждый документ идёт через свой adapter, но результат сводится к одному document selection contract.</p>
          </article>
          <article class="architecture-card">
            <strong>Layout Modes</strong>
            <p>PDF embed, plain text, tabular preview и sandbox HTML живут как mode-подтипы одной модели.</p>
          </article>
          <article class="architecture-card">
            <strong>Search Layer</strong>
            <p>Quick find работает по нормализованному text layer, а не зависит от конкретного renderer или DOM.</p>
          </article>
          <article class="architecture-card">
            <strong>Capability Honesty</strong>
            <p>DOCX/XLSX/PPTX и другие сложные форматы уже заведены, но честно маркируются как foundation only.</p>
          </article>
          <article class="architecture-card">
            <strong>Shared Workspace</strong>
            <p>Маршрут остаётся один: stage, fullscreen, drag-and-drop и summary-панели переиспользуются между семьями.</p>
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
  background-size: 24px 24px, 24px 24px, 24px 24px, 24px 24px, auto;
  background-position:
    0 0,
    0 12px,
    12px -12px,
    -12px 0,
    0 0;
}

.viewer-image-frame,
.viewer-document-frame {
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

.viewer-document-frame__embed {
  width: 100%;
  min-height: 70vh;
  border: 0;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: var(--shadow-pressed);
}

.document-text,
.document-table,
.document-workbook,
.document-slide-grid,
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

.outline-card {
  grid-template-columns: auto 1fr;
  align-items: center;
}

.outline-card--interactive {
  cursor: pointer;
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
  .metadata-group__header,
  .format-card__meta,
  .metadata-editor__footer,
  .document-table__summary {
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

  .viewer-image-frame,
  .viewer-document-frame {
    min-height: 340px;
  }

  .viewer-document-frame__embed {
    min-height: 56vh;
  }

  .color-lab__swatch,
  .color-lab__loupe {
    width: 100%;
    min-width: 0;
  }
}
</style>
