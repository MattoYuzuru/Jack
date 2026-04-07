<script setup lang="ts">
import { computed, reactive } from 'vue'
import { RouterLink } from 'vue-router'
import { usePdfToolkitWorkspace } from '../features/pdf-toolkit/composables/usePdfToolkitWorkspace'

const workspace = reactive(usePdfToolkitWorkspace())

const entryAcceptAttribute = computed(() =>
  [workspace.pdfAcceptAttribute, workspace.importAcceptAttribute].filter(Boolean).join(','),
)

const jobStatusLabels: Record<string, string> = {
  QUEUED: 'В очереди',
  RUNNING: 'В работе',
  COMPLETED: 'Готово',
  FAILED: 'Ошибка',
  CANCELLED: 'Остановлено',
}

function formatJobStatusLabel(status: string | null | undefined): string {
  if (!status) {
    return 'В очереди'
  }

  return jobStatusLabels[status] ?? status
}

function formatRuntimeLabel(value: string | null | undefined): string {
  if (!value) {
    return 'Подготовка документа'
  }

  const normalized = value.trim().toLowerCase()

  if (normalized.includes('slideshow')) {
    return 'Видео из слайдов'
  }

  if (normalized.includes('contact-sheet')) {
    return 'Единый лист просмотра'
  }

  if (normalized.includes('spreadsheet')) {
    return 'Табличный экспорт'
  }

  if (normalized.includes('slide') && normalized.includes('pdf')) {
    return 'PDF из слайдов'
  }

  if (normalized.includes('pdf')) {
    return 'Обработка PDF'
  }

  return value
}

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
          <p class="eyebrow">Jack · PDF Toolkit</p>
          <p class="brand-lockup__title">Работа с PDF</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <span class="chip-pill">PDF-инструменты</span>
        <span class="chip-pill chip-pill--accent">Объединение · OCR · Защита</span>
      </div>
    </header>

    <section class="pdf-hero-grid">
      <article class="panel-surface pdf-hero-card">
        <RouterLink class="back-link" to="/">На главную</RouterLink>
        <p class="eyebrow">PDF Toolkit</p>
        <h1>Открой PDF и сразу запусти нужную операцию.</h1>
        <p class="lead">
          Объединение, разбиение, OCR, подпись, защита и скрытие данных собраны в одном рабочем
          окне.
        </p>

        <div class="hero-chip-row">
          <span class="chip-pill">Объединение</span>
          <span class="chip-pill">Разделение</span>
          <span class="chip-pill">OCR</span>
          <span class="chip-pill">Подпись</span>
          <span class="chip-pill">Защита</span>
          <span class="chip-pill">Редактура</span>
        </div>
      </article>

      <article class="panel-surface intake-card">
        <p class="eyebrow">Загрузка</p>
        <h2>Выбери PDF или совместимый файл.</h2>
        <p class="intake-copy">
          Если исходник не в PDF, Jack сначала подготовит PDF-версию и сразу откроет её здесь.
        </p>

        <label class="upload-field">
          <span>Выбрать файл</span>
          <input :accept="entryAcceptAttribute" type="file" @change="handleSourceChange" />
        </label>

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
            <p class="eyebrow">Предпросмотр</p>
            <h2>Текущий PDF</h2>
          </div>
          <button
            v-if="workspace.hasDocument"
            class="action-button"
            type="button"
            @click="workspace.clearDocument"
          >
            Очистить
          </button>
        </div>

        <template v-if="workspace.document">
          <div class="preview-meta">
            <span class="chip-pill chip-pill--compact">{{ workspace.document.sourceLabel }}</span>
            <span class="chip-pill chip-pill--compact chip-pill--accent">
              {{
                workspace.document.sourceRouteKind === 'convert-to-pdf'
                  ? 'Собран из другого формата'
                  : 'Исходный PDF'
              }}
            </span>
            <span v-if="workspace.importedFromLabel" class="chip-pill chip-pill--compact">
              Из {{ workspace.importedFromLabel }}
            </span>
          </div>

          <div class="preview-frame">
            <iframe :src="workspace.document.objectUrl" title="Предпросмотр PDF" />
          </div>

          <div class="facts-grid">
            <article class="fact-block">
              <h3>Основные сведения</h3>
              <dl>
                <div v-for="fact in workspace.document.summary" :key="fact.label">
                  <dt>{{ fact.label }}</dt>
                  <dd>{{ fact.value }}</dd>
                </div>
              </dl>
            </article>
            <article class="fact-block">
              <h3>Предупреждения</h3>
              <p v-if="workspace.document.warnings.length === 0" class="muted-copy">
                Для текущего документа дополнительных предупреждений нет.
              </p>
              <ul v-else class="warning-list">
                <li v-for="warning in workspace.document.warnings" :key="warning">{{ warning }}</li>
              </ul>
            </article>
          </div>

          <article class="search-card">
            <div class="section-head section-head--tight">
              <div>
                <p class="eyebrow">Текст документа</p>
                <h3>{{ workspace.document.pageCount ?? 'Неизвестно' }} стр.</h3>
              </div>
              <span class="chip-pill chip-pill--compact">Постраничный просмотр</span>
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
          <p class="eyebrow">Защищённый PDF</p>
          <h3>{{ workspace.lockedDocumentFile.name }}</h3>
          <p>
            Документ не удалось открыть без пароля. Выбери действие `Снять защиту`, укажи текущий
            пароль и попробуй снова.
          </p>
        </article>

        <article v-else class="empty-card">
          <p class="eyebrow">Документ ещё не открыт</p>
          <h3>Сначала загрузи документ.</h3>
          <p>После выбора здесь появятся страницы, текст и доступные операции.</p>
        </article>
      </article>

      <article class="panel-surface operations-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">Операции</p>
            <h2>Действия с документом</h2>
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
            <span class="chip-pill chip-pill--compact">{{ operation.statusLabel }}</span>
          </button>
        </div>

        <div v-if="workspace.activeOperation" class="operation-panel">
          <p class="muted-copy">{{ workspace.activeOperation.detail }}</p>

          <div v-if="workspace.activeOperation.id === 'merge'" class="control-stack">
            <label class="upload-field upload-field--secondary">
              <span>Добавить PDF для объединения</span>
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
              <span>Диапазоны страниц</span>
              <textarea
                v-model="workspace.splitRangesInput"
                rows="4"
                placeholder="1-2&#10;3&#10;4-6"
              />
            </label>
            <p class="muted-copy">
              Один диапазон на строку. Если оставить пусто, документ будет разбит на отдельные
              страницы.
            </p>
          </div>

          <div
            v-if="['rotate', 'sign', 'redact'].includes(workspace.activeOperation.id)"
            class="control-grid"
          >
            <label class="field-block">
              <span>Страницы</span>
              <input v-model="workspace.pageSelection" placeholder="1-3,5" type="text" />
            </label>
            <label v-if="workspace.activeOperation.id === 'rotate'" class="field-block">
              <span>Поворот</span>
              <select v-model="workspace.rotationDegrees">
                <option v-for="value in workspace.rotationOptions" :key="value" :value="value">
                  {{ value }}°
                </option>
              </select>
            </label>
          </div>

          <div v-if="workspace.activeOperation.id === 'reorder'" class="control-stack">
            <label class="field-block">
              <span>Новый порядок страниц</span>
              <input v-model="workspace.pageOrderInput" placeholder="3,1,2" type="text" />
            </label>
            <p class="muted-copy">
              Если указать не все страницы, сервис соберёт только выбранную часть документа.
            </p>
          </div>

          <div v-if="workspace.activeOperation.id === 'ocr'" class="control-stack">
            <label class="field-block">
              <span>Язык OCR</span>
              <input v-model="workspace.ocrLanguage" placeholder="eng" type="text" />
            </label>
            <p class="muted-copy">
              По умолчанию используется `eng`. Если в окружении добавлены другие языки OCR, можно
              указать их код вручную.
            </p>
          </div>

          <div v-if="workspace.activeOperation.id === 'sign'" class="control-stack">
            <div class="control-grid">
              <label class="field-block">
                <span>Текст подписи</span>
                <input v-model="workspace.signatureText" placeholder="Jack QA" type="text" />
              </label>
              <label class="field-block">
                <span>Расположение</span>
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
              <span>Изображение подписи</span>
              <input accept="image/*" type="file" @change="handleSignatureImageChange" />
            </label>

            <label class="toggle-field">
              <input v-model="workspace.includeSignatureDate" type="checkbox" />
              <span>Добавить дату внутрь штампа</span>
            </label>
          </div>

          <div v-if="workspace.activeOperation.id === 'redact'" class="control-stack">
            <label class="field-block">
              <span>Что скрыть</span>
              <textarea
                v-model="workspace.redactTermsInput"
                rows="4"
                placeholder="Номер договора 123&#10;Внутренний ID"
              />
            </label>
            <p class="muted-copy">
              Скрытие выполняется по текстовому слою, после чего итоговый PDF собирается заново.
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
            <span>Текущий пароль</span>
            <input
              v-model="workspace.currentPassword"
              placeholder="Нужен только для защищённых PDF"
              type="password"
            />
          </div>

          <div v-if="workspace.activeOperation.id === 'protect'" class="control-stack">
            <div class="control-grid">
              <label class="field-block">
                <span>Пароль владельца</span>
                <input
                  v-model="workspace.ownerPassword"
                  placeholder="owner-secret"
                  type="password"
                />
              </label>
              <label class="field-block">
                <span>Пароль для открытия</span>
                <input
                  v-model="workspace.userPassword"
                  placeholder="необязательно"
                  type="password"
                />
              </label>
            </div>

            <div class="toggle-grid">
              <label class="toggle-field">
                <input v-model="workspace.allowPrinting" type="checkbox" />
                <span>Разрешить печать</span>
              </label>
              <label class="toggle-field">
                <input v-model="workspace.allowCopying" type="checkbox" />
                <span>Разрешить копирование</span>
              </label>
              <label class="toggle-field">
                <input v-model="workspace.allowModifying" type="checkbox" />
                <span>Разрешить изменения</span>
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
              Выполнить: {{ workspace.activeOperation.label }}
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.activeJobId || workspace.isCancelling"
              @click="workspace.cancelOperation"
            >
              Остановить
            </button>
          </div>

          <div v-if="workspace.activeJobId" class="job-card">
            <p class="job-card__label">
              Задача {{ workspace.activeJobId }} ·
              {{ formatJobStatusLabel(workspace.activeJobStatus) }}
            </p>
            <div class="job-progress">
              <span :style="{ width: `${workspace.activeJobProgressPercent}%` }"></span>
            </div>
            <p class="muted-copy">
              {{ workspace.activeJobProgressPercent }}% · {{ workspace.processingMessage }}
            </p>
          </div>
        </div>
      </article>
    </section>

    <section class="results-grid">
      <article class="panel-surface history-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">История</p>
            <h2>Последние результаты</h2>
          </div>
          <span class="chip-pill">{{ workspace.resultHistory.length }} результатов</span>
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
            <small>{{ formatRuntimeLabel(entry.runtimeLabel) }}</small>
          </button>
        </div>
        <p v-else class="muted-copy">
          После первой операции здесь появятся результаты, к которым можно быстро вернуться.
        </p>
      </article>

      <article class="panel-surface result-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">Текущий результат</p>
            <h2>{{ workspace.result?.fileName || 'Результата пока нет' }}</h2>
          </div>
          <div class="result-actions">
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="!workspace.result"
              @click="workspace.downloadResult()"
            >
              Скачать результат
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.result"
              @click="workspace.loadResultAsCurrent()"
            >
              Открыть как текущий
            </button>
          </div>
        </div>

        <template v-if="workspace.result">
          <div class="result-preview">
            <iframe
              v-if="workspace.result.previewMimeType === 'application/pdf'"
              :src="workspace.result.previewObjectUrl"
              title="Предпросмотр результата PDF Toolkit"
            />
            <div v-else class="empty-card empty-card--compact">
              <p class="eyebrow">Просмотр</p>
              <h3>{{ workspace.result.previewFileName }}</h3>
              <p>Для этого результата доступно скачивание без встроенного preview.</p>
            </div>
          </div>

          <div class="result-meta-grid">
            <article class="fact-block">
              <h3>Параметры операции</h3>
              <dl>
                <div v-for="fact in workspace.result.operationFacts" :key="fact.label">
                  <dt>{{ fact.label }}</dt>
                  <dd>{{ fact.value }}</dd>
                </div>
              </dl>
            </article>
            <article class="fact-block">
              <h3>Предупреждения</h3>
              <ul v-if="workspace.result.warnings.length" class="warning-list">
                <li v-for="warning in workspace.result.warnings" :key="warning">{{ warning }}</li>
              </ul>
              <p v-else class="muted-copy">Для этого результата предупреждений нет.</p>
            </article>
          </div>

          <div class="download-strip">
            <button class="action-button" type="button" @click="workspace.downloadPreview()">
              Скачать предпросмотр
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.result.textObjectUrl"
              @click="workspace.downloadTextArtifact()"
            >
              Скачать текст
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
          <p class="eyebrow">Пока пусто</p>
          <h3>Результат появится здесь.</h3>
          <p>После операции сюда загрузится новый PDF или связанный артефакт.</p>
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
  margin: 18px 0 0;
  max-width: 58ch;
  font-size: 1rem;
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
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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

.operation-pill .chip-pill {
  width: fit-content;
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
