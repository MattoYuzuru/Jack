<script setup lang="ts">
import { computed, reactive } from 'vue'
import { RouterLink } from 'vue-router'
import { usePdfToolkitWorkspace } from '../features/pdf-toolkit/composables/usePdfToolkitWorkspace'

const workspace = reactive(usePdfToolkitWorkspace())

const entryAcceptAttribute = computed(() =>
  [workspace.pdfAcceptAttribute, workspace.importAcceptAttribute].filter(Boolean).join(','),
)

function handleSourceChange(event: Event): void {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (!file) {
    return
  }

  void workspace.openSource(file)
  input.value = ''
}

function handleMergeChange(event: Event): void {
  const input = event.target as HTMLInputElement | null
  workspace.setMergeFiles(Array.from(input?.files ?? []))
  if (input) {
    input.value = ''
  }
}

function handleSignatureImageChange(event: Event): void {
  const input = event.target as HTMLInputElement | null
  workspace.setSignatureImageFile(input?.files?.[0] ?? null)
  if (input) {
    input.value = ''
  }
}

function formatBytes(value: number): string {
  if (value >= 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(1)} MB`
  }
  if (value >= 1024) {
    return `${Math.round(value / 1024)} KB`
  }
  return `${value} B`
}
</script>

<template>
  <main class="workspace-shell pdf-toolkit-shell">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Iteration 05 · PDF Toolkit</p>
          <p class="brand-lockup__title">PDF Workspace</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <span class="chip-pill">PDF_TOOLKIT + VIEWER_RESOLVE</span>
        <span class="chip-pill chip-pill--accent">Merge · OCR · Protect</span>
      </div>
    </header>

    <section class="pdf-hero-grid">
      <article class="panel-surface pdf-hero-card">
        <RouterLink class="back-link" to="/">Back to Home</RouterLink>
        <p class="eyebrow">PDF Toolkit Route</p>
        <h1>Page-aware PDF операции теперь живут в отдельном backend-first workspace.</h1>
        <p class="lead">
          Модуль reuse'ит `VIEWER_RESOLVE` для preview, `IMAGE_CONVERT` и `OFFICE_CONVERT` для
          import-to-PDF flows, а merge/split/rotate/OCR/redact/protect больше не пытаются жить в
          браузерных обходах и все идут в `PDF_TOOLKIT`.
        </p>

        <div class="hero-chip-row">
          <span class="chip-pill">Merge stack</span>
          <span class="chip-pill">Split bundle</span>
          <span class="chip-pill">OCR text export</span>
          <span class="chip-pill">Visible e-sign stamp</span>
          <span class="chip-pill">Term redaction</span>
          <span class="chip-pill">Password flows</span>
        </div>
      </article>

      <article class="panel-surface intake-card">
        <p class="eyebrow">Intake</p>
        <h2>Открой PDF напрямую или заведи совместимый source через import-to-PDF.</h2>
        <p class="intake-copy">
          Совместимые image/office форматы сначала проходят backend conversion в PDF, а затем
          автоматически переводятся в этот workspace.
        </p>

        <label class="upload-field">
          <span>Select source</span>
          <input :accept="entryAcceptAttribute" type="file" @change="handleSourceChange" />
        </label>

        <div class="intake-pills">
          <span class="chip-pill chip-pill--compact"
            >Direct: {{ workspace.pdfAcceptAttribute }}</span
          >
          <span class="chip-pill chip-pill--compact"
            >Import: {{ workspace.importAcceptAttribute || 'none' }}</span
          >
        </div>

        <p v-if="workspace.processingMessage" class="status-message">
          {{ workspace.processingMessage }}
        </p>
        <p v-if="workspace.errorMessage" class="error-message">{{ workspace.errorMessage }}</p>
      </article>
    </section>

    <section class="pdf-main-grid">
      <article class="panel-surface preview-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">Preview</p>
            <h2>Текущий PDF</h2>
          </div>
          <button
            v-if="workspace.hasDocument"
            class="action-button"
            type="button"
            @click="workspace.clearDocument"
          >
            Clear
          </button>
        </div>

        <template v-if="workspace.document">
          <div class="preview-meta">
            <span class="chip-pill chip-pill--compact">{{ workspace.document.sourceLabel }}</span>
            <span class="chip-pill chip-pill--compact chip-pill--accent">
              {{
                workspace.document.sourceRouteKind === 'convert-to-pdf'
                  ? 'Imported to PDF'
                  : 'Direct PDF'
              }}
            </span>
            <span v-if="workspace.importedFromLabel" class="chip-pill chip-pill--compact">
              From {{ workspace.importedFromLabel }}
            </span>
          </div>

          <div class="preview-frame">
            <iframe :src="workspace.document.objectUrl" title="PDF preview" />
          </div>

          <div class="facts-grid">
            <article class="fact-block">
              <h3>Document facts</h3>
              <dl>
                <div v-for="fact in workspace.document.summary" :key="fact.label">
                  <dt>{{ fact.label }}</dt>
                  <dd>{{ fact.value }}</dd>
                </div>
              </dl>
            </article>
            <article class="fact-block">
              <h3>Warnings</h3>
              <p v-if="workspace.document.warnings.length === 0" class="muted-copy">
                Нет backend warnings для текущего preview.
              </p>
              <ul v-else class="warning-list">
                <li v-for="warning in workspace.document.warnings" :key="warning">{{ warning }}</li>
              </ul>
            </article>
          </div>

          <article class="search-card">
            <div class="section-head section-head--tight">
              <div>
                <p class="eyebrow">Search Layer</p>
                <h3>{{ workspace.document.pageCount ?? 'Unknown' }} pages</h3>
              </div>
              <span class="chip-pill chip-pill--compact">{{
                workspace.document.previewLabel
              }}</span>
            </div>
            <textarea
              class="text-preview"
              :value="workspace.document.searchableText"
              readonly
              spellcheck="false"
            />
          </article>
        </template>

        <article v-else-if="workspace.lockedDocumentFile" class="locked-card">
          <p class="eyebrow">Protected PDF</p>
          <h3>{{ workspace.lockedDocumentFile.name }}</h3>
          <p>
            Preview не поднялся. Обычно это значит, что PDF защищён паролем. Выбери `Unlock PDF`,
            укажи `current password` и перезапусти документ в workspace.
          </p>
        </article>

        <article v-else class="empty-card">
          <p class="eyebrow">No document</p>
          <h3>Сначала открой PDF или совместимый source.</h3>
          <p>
            После intake workspace покажет backend PDF preview, warnings, searchable text и все
            операции страницы справа.
          </p>
        </article>
      </article>

      <article class="panel-surface operations-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">Operations</p>
            <h2>PDF control rail</h2>
          </div>
          <span v-if="workspace.activeOperation" class="chip-pill chip-pill--accent">
            {{ workspace.activeOperation.label }}
          </span>
        </div>

        <div class="operation-selector">
          <button
            v-for="operation in workspace.availableOperations"
            :key="operation.id"
            class="operation-pill"
            :class="{ 'operation-pill--active': workspace.selectedOperationId === operation.id }"
            type="button"
            @click="workspace.selectedOperationId = operation.id"
          >
            <strong>{{ operation.label }}</strong>
            <span>{{ operation.detail }}</span>
          </button>
        </div>

        <div v-if="workspace.activeOperation" class="operation-panel">
          <p class="muted-copy">{{ workspace.activeOperation.detail }}</p>

          <div v-if="workspace.activeOperation.id === 'merge'" class="control-stack">
            <label class="upload-field upload-field--secondary">
              <span>Add PDFs to merge</span>
              <input accept=".pdf" multiple type="file" @change="handleMergeChange" />
            </label>
            <div class="file-pill-row">
              <span
                v-for="file in workspace.mergeFiles"
                :key="file.name"
                class="chip-pill chip-pill--compact"
              >
                {{ file.name }}
              </span>
            </div>
          </div>

          <div v-if="workspace.activeOperation.id === 'split'" class="control-stack">
            <label class="field-block">
              <span>Split ranges</span>
              <textarea
                v-model="workspace.splitRangesInput"
                rows="4"
                placeholder="1-2&#10;3&#10;4-6"
              />
            </label>
            <p class="muted-copy">
              Один диапазон на строку. Если оставить пусто, backend разрежет документ на отдельные
              страницы.
            </p>
          </div>

          <div
            v-if="['rotate', 'sign', 'redact'].includes(workspace.activeOperation.id)"
            class="control-grid"
          >
            <label class="field-block">
              <span>Page selection</span>
              <input v-model="workspace.pageSelection" placeholder="1-3,5" type="text" />
            </label>
            <label v-if="workspace.activeOperation.id === 'rotate'" class="field-block">
              <span>Rotation</span>
              <select v-model="workspace.rotationDegrees">
                <option v-for="value in workspace.rotationOptions" :key="value" :value="value">
                  {{ value }}°
                </option>
              </select>
            </label>
          </div>

          <div v-if="workspace.activeOperation.id === 'reorder'" class="control-stack">
            <label class="field-block">
              <span>Page order</span>
              <input v-model="workspace.pageOrderInput" placeholder="3,1,2" type="text" />
            </label>
            <p class="muted-copy">
              Меньший список работает как extract subset. Дубликаты не допускаются.
            </p>
          </div>

          <div v-if="workspace.activeOperation.id === 'ocr'" class="control-stack">
            <label class="field-block">
              <span>OCR language</span>
              <input v-model="workspace.ocrLanguage" placeholder="eng" type="text" />
            </label>
            <p class="muted-copy">
              Текущий контейнерный профиль по умолчанию рассчитан на `eng`. Дополнительные языки
              можно подключать через backend runtime.
            </p>
          </div>

          <div v-if="workspace.activeOperation.id === 'sign'" class="control-stack">
            <div class="control-grid">
              <label class="field-block">
                <span>Signature text</span>
                <input v-model="workspace.signatureText" placeholder="Jack QA" type="text" />
              </label>
              <label class="field-block">
                <span>Placement</span>
                <select v-model="workspace.signaturePlacement">
                  <option
                    v-for="placement in workspace.signaturePlacements"
                    :key="placement.value"
                    :value="placement.value"
                  >
                    {{ placement.label }}
                  </option>
                </select>
              </label>
            </div>

            <label class="upload-field upload-field--secondary">
              <span>Optional signature image</span>
              <input accept="image/*" type="file" @change="handleSignatureImageChange" />
            </label>

            <label class="toggle-field">
              <input v-model="workspace.includeSignatureDate" type="checkbox" />
              <span>Attach signature date metadata inside stamp</span>
            </label>
          </div>

          <div v-if="workspace.activeOperation.id === 'redact'" class="control-stack">
            <label class="field-block">
              <span>Terms to redact</span>
              <textarea
                v-model="workspace.redactTermsInput"
                rows="4"
                placeholder="Secret token 123&#10;Internal ID"
              />
            </label>
            <p class="muted-copy">
              Redaction идёт по text layer и пересобирает итоговый PDF как rasterized artifact.
            </p>
          </div>

          <div
            v-if="
              ['unlock', 'protect', 'merge', 'rotate', 'reorder', 'ocr', 'sign', 'redact'].includes(
                workspace.activeOperation.id,
              )
            "
            class="field-block"
          >
            <span>Current password</span>
            <input
              v-model="workspace.currentPassword"
              placeholder="Only if source PDF is protected"
              type="password"
            />
          </div>

          <div v-if="workspace.activeOperation.id === 'protect'" class="control-stack">
            <div class="control-grid">
              <label class="field-block">
                <span>Owner password</span>
                <input
                  v-model="workspace.ownerPassword"
                  placeholder="owner-secret"
                  type="password"
                />
              </label>
              <label class="field-block">
                <span>User password</span>
                <input v-model="workspace.userPassword" placeholder="optional" type="password" />
              </label>
            </div>

            <div class="toggle-grid">
              <label class="toggle-field">
                <input v-model="workspace.allowPrinting" type="checkbox" />
                <span>Allow printing</span>
              </label>
              <label class="toggle-field">
                <input v-model="workspace.allowCopying" type="checkbox" />
                <span>Allow copying</span>
              </label>
              <label class="toggle-field">
                <input v-model="workspace.allowModifying" type="checkbox" />
                <span>Allow modifying</span>
              </label>
            </div>
          </div>

          <div class="action-row">
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="!workspace.canRunOperation"
              @click="workspace.runOperation"
            >
              Run {{ workspace.activeOperation.label }}
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.activeJobId || workspace.isCancelling"
              @click="workspace.cancelOperation"
            >
              Cancel Job
            </button>
          </div>

          <div v-if="workspace.activeJobId" class="job-card">
            <p class="job-card__label">
              Job {{ workspace.activeJobId }} · {{ workspace.activeJobStatus || 'QUEUED' }}
            </p>
            <div class="job-progress">
              <span :style="{ width: `${workspace.activeJobProgressPercent}%` }"></span>
            </div>
            <p class="muted-copy">
              {{ workspace.activeJobProgressPercent }}% complete · {{ workspace.processingMessage }}
            </p>
          </div>
        </div>
      </article>
    </section>

    <section class="results-grid">
      <article class="panel-surface history-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">History</p>
            <h2>Result rail</h2>
          </div>
          <span class="chip-pill">{{ workspace.resultHistory.length }} results</span>
        </div>

        <div v-if="workspace.hasResultHistory" class="history-list">
          <button
            v-for="entry in workspace.resultHistory"
            :key="entry.id"
            class="history-item"
            :class="{ 'history-item--active': workspace.result?.id === entry.id }"
            type="button"
            @click="workspace.selectResult(entry.id)"
          >
            <strong>{{ entry.operation }}</strong>
            <span>{{ entry.fileName }}</span>
            <small>{{ entry.runtimeLabel }}</small>
          </button>
        </div>
        <p v-else class="muted-copy">
          После первой операции сюда попадут manifest-driven result entries и follow-up downloads.
        </p>
      </article>

      <article class="panel-surface result-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">Selected Result</p>
            <h2>{{ workspace.result?.fileName || 'No result yet' }}</h2>
          </div>
          <div class="result-actions">
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="!workspace.result"
              @click="workspace.downloadResult()"
            >
              Download result
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.result"
              @click="workspace.loadResultAsCurrent()"
            >
              Load as current
            </button>
          </div>
        </div>

        <template v-if="workspace.result">
          <div class="result-preview">
            <iframe
              v-if="workspace.result.previewMimeType === 'application/pdf'"
              :src="workspace.result.previewObjectUrl"
              title="PDF toolkit result preview"
            />
            <div v-else class="empty-card empty-card--compact">
              <p class="eyebrow">Preview</p>
              <h3>{{ workspace.result.previewFileName }}</h3>
              <p>Preview artifact не является PDF. Используй download для просмотра.</p>
            </div>
          </div>

          <div class="result-meta-grid">
            <article class="fact-block">
              <h3>Operation facts</h3>
              <dl>
                <div v-for="fact in workspace.result.operationFacts" :key="fact.label">
                  <dt>{{ fact.label }}</dt>
                  <dd>{{ fact.value }}</dd>
                </div>
              </dl>
            </article>
            <article class="fact-block">
              <h3>Warnings</h3>
              <ul v-if="workspace.result.warnings.length" class="warning-list">
                <li v-for="warning in workspace.result.warnings" :key="warning">{{ warning }}</li>
              </ul>
              <p v-else class="muted-copy">Нет backend warnings для этого result.</p>
            </article>
          </div>

          <div class="download-strip">
            <button class="action-button" type="button" @click="workspace.downloadPreview()">
              Download preview
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.result.textObjectUrl"
              @click="workspace.downloadTextArtifact()"
            >
              Download text export
            </button>
            <span class="chip-pill chip-pill--compact">
              {{ formatBytes(workspace.result.resultBlob.size) }}
            </span>
            <span class="chip-pill chip-pill--compact">
              {{ workspace.result.resultMimeType }}
            </span>
          </div>
        </template>

        <div v-else class="empty-card">
          <p class="eyebrow">Idle</p>
          <h3>Result preview появится здесь.</h3>
          <p>
            Для PDF outputs можно сразу перезагрузить их в текущий workspace как новый active
            document.
          </p>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.pdf-toolkit-shell {
  padding-bottom: 54px;
}

.pdf-hero-grid,
.pdf-main-grid,
.results-grid {
  display: grid;
  gap: 22px;
  margin-top: 22px;
}

.pdf-hero-grid {
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.86fr);
}

.pdf-main-grid {
  grid-template-columns: minmax(0, 1.18fr) minmax(360px, 0.9fr);
}

.results-grid {
  grid-template-columns: minmax(320px, 0.64fr) minmax(0, 1fr);
}

.pdf-hero-card,
.intake-card,
.preview-card,
.operations-card,
.history-card,
.result-card {
  padding: 28px;
}

h1,
h2,
h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  letter-spacing: -0.03em;
}

h1 {
  margin-top: 18px;
  max-width: 10.8ch;
  font-size: clamp(2.8rem, 4.8vw, 5rem);
  line-height: 0.94;
}

.lead,
.intake-copy,
.muted-copy,
.locked-card p,
.empty-card p {
  color: var(--text-soft);
}

.lead {
  margin: 20px 0 0;
  max-width: 64ch;
  font-size: 1.04rem;
}

.hero-chip-row,
.preview-meta,
.file-pill-row,
.download-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hero-chip-row {
  margin-top: 26px;
}

.upload-field,
.field-block {
  display: grid;
  gap: 10px;
}

.upload-field span,
.field-block span {
  color: var(--text-main);
  font-size: 0.9rem;
  font-weight: 700;
}

.upload-field input,
.field-block input,
.field-block textarea,
.field-block select,
.text-preview {
  width: 100%;
  padding: 14px 16px;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}

.field-block textarea,
.text-preview {
  min-height: 118px;
  resize: vertical;
}

.upload-field--secondary input {
  padding: 12px 14px;
}

.intake-pills {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.status-message,
.error-message {
  margin: 16px 0 0;
  padding: 14px 16px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-pressed);
}

.status-message {
  background: rgba(255, 249, 239, 0.88);
  color: var(--text-main);
}

.error-message {
  background: rgba(243, 138, 85, 0.14);
  color: #8f3f1f;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.section-head--tight {
  align-items: center;
}

.preview-frame,
.result-preview {
  margin-top: 18px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-pressed);
  background: rgba(255, 251, 245, 0.92);
}

.preview-frame iframe,
.result-preview iframe {
  display: block;
  width: 100%;
  min-height: 620px;
  border: 0;
  background: white;
}

.facts-grid,
.result-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.fact-block,
.search-card,
.job-card,
.locked-card,
.empty-card {
  padding: 18px;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.search-card {
  margin-top: 18px;
}

dl {
  display: grid;
  gap: 12px;
  margin: 14px 0 0;
}

dt {
  color: var(--text-soft);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

dd {
  margin: 4px 0 0;
  color: var(--text-main);
  font-weight: 700;
}

.warning-list {
  display: grid;
  gap: 10px;
  margin: 14px 0 0;
  padding-left: 18px;
  color: var(--text-soft);
}

.operation-selector {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}

.operation-pill {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}

.operation-pill strong {
  font-size: 1rem;
}

.operation-pill span {
  color: var(--text-soft);
  font-size: 0.9rem;
}

.operation-pill:hover,
.operation-pill--active {
  transform: translateY(-2px);
  box-shadow: var(--shadow-floating);
}

.operation-panel {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.control-stack,
.toggle-grid,
.control-grid {
  display: grid;
  gap: 14px;
}

.control-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.toggle-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.toggle-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 14px 16px;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
  font-weight: 700;
}

.action-row,
.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.job-card__label {
  margin: 0;
  color: var(--accent-cool-strong);
  font-weight: 800;
}

.job-progress {
  height: 10px;
  margin-top: 14px;
  border-radius: 999px;
  background: rgba(16, 36, 38, 0.08);
  overflow: hidden;
}

.job-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent-coral), var(--accent-amber));
}

.history-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.history-item {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}

.history-item strong {
  color: var(--accent-cool-strong);
}

.history-item span,
.history-item small {
  color: var(--text-soft);
}

.history-item--active {
  box-shadow: var(--shadow-floating);
}

.download-strip {
  margin-top: 18px;
}

.empty-card--compact {
  min-height: 280px;
  display: grid;
  place-content: center;
  text-align: center;
}

@media (max-width: 1180px) {
  .pdf-main-grid,
  .results-grid,
  .pdf-hero-grid,
  .facts-grid,
  .result-meta-grid,
  .control-grid,
  .toggle-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .pdf-hero-card,
  .intake-card,
  .preview-card,
  .operations-card,
  .history-card,
  .result-card {
    padding: 22px;
  }

  .preview-frame iframe,
  .result-preview iframe {
    min-height: 420px;
  }
}
</style>
