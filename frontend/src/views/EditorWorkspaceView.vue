<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useEditorWorkspace } from '../features/editor/composables/useEditorWorkspace'

type EditorPanelTab = 'preview' | 'diagnostics' | 'outline' | 'exports'

const workspace = useEditorWorkspace()
const activeTab = ref<EditorPanelTab>('preview')
const sideTabs: EditorPanelTab[] = ['preview', 'diagnostics', 'outline', 'exports']
const fileInput = ref<HTMLInputElement | null>(null)
const textarea = ref<HTMLTextAreaElement | null>(null)
const lineGutter = ref<HTMLElement | null>(null)

const summaryFacts = computed(() => {
  if (workspace.serverSummary.value.length) {
    return workspace.serverSummary.value
  }

  return [
    { label: 'Формат', value: workspace.activeFormat.value?.label ?? 'Не выбран' },
    { label: 'Символов', value: String(workspace.characterCount.value) },
    { label: 'Строк', value: String(workspace.lineCount.value) },
    { label: 'Слов', value: String(workspace.wordCount.value) },
  ]
})

const shortcutPills = computed(() => ['Markdown', 'HTML', 'JSON', 'YAML', 'TXT'])

function formatTabLabel(tab: EditorPanelTab): string {
  switch (tab) {
    case 'preview':
      return 'Просмотр'
    case 'diagnostics':
      return 'Проверка'
    case 'outline':
      return 'Структура'
    case 'exports':
      return 'Экспорт'
  }
}

async function hydrateWorkspace(): Promise<void> {
  try {
    await workspace.hydrateCapabilities()
  } catch (error) {
    workspace.errorMessage.value =
      error instanceof Error ? error.message : 'Не удалось загрузить доступные форматы редактора.'
  }
}

function triggerFileOpen(): void {
  fileInput.value?.click()
}

async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (!file) {
    return
  }

  try {
    await workspace.openFile(file)
  } catch (error) {
    workspace.errorMessage.value =
      error instanceof Error ? error.message : 'Не удалось открыть выбранный файл.'
  } finally {
    if (input) {
      input.value = ''
    }
  }
}

function onEditorScroll(event: Event): void {
  if (!lineGutter.value) {
    return
  }

  lineGutter.value.scrollTop = (event.target as HTMLTextAreaElement).scrollTop
}

function onTemplateChange(): void {
  workspace.applyTemplate(workspace.selectedTemplateId.value)
}

function onHelperClick(actionId: string): void {
  workspace.applyHelperAction(actionId, textarea.value)
}

function onEditorKeydown(event: KeyboardEvent): void {
  workspace.handleEditorKeydown(event, textarea.value)
}

onMounted(() => {
  void hydrateWorkspace()
})
</script>

<template>
  <main class="workspace-shell editor-workspace">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack · Editor</p>
          <p class="brand-lockup__title">Редактор документов и текстов</p>
        </div>
      </div>

      <div class="editor-topbar__actions">
        <RouterLink class="back-link" to="/">На главную</RouterLink>
        <span class="chip-pill">Несколько текстовых форматов</span>
        <span class="chip-pill chip-pill--accent">Просмотр, проверка, экспорт</span>
      </div>
    </header>

    <section class="editor-hero">
      <article class="panel-surface editor-hero__copy">
        <p class="eyebrow">Editor</p>
        <h1>Открой черновик и правь его в одном рабочем окне.</h1>
        <p class="lead">
          Markdown, HTML, CSS, JavaScript, JSON, YAML и обычный текст редактируются, проверяются и
          экспортируются без лишних экранов.
        </p>

        <div class="signal-row">
          <span
            v-for="pill in shortcutPills"
            :key="pill"
            class="chip-pill chip-pill--compact editor-shortcut-pill"
          >
            {{ pill }}
          </span>
        </div>
      </article>

      <article class="panel-surface editor-hero__status">
        <p class="eyebrow">Сводка</p>
        <div class="editor-status-grid">
          <article class="editor-status-card">
            <strong>{{ workspace.activeFormat.value?.label ?? 'Загрузка…' }}</strong>
            <span>Текущий формат</span>
          </article>
          <article class="editor-status-card">
            <strong>{{ workspace.lineCount.value }}</strong>
            <span>Строк в черновике</span>
          </article>
          <article class="editor-status-card">
            <strong>{{ workspace.diagnosticsBySeverity.value.error }}</strong>
            <span>Ошибок проверки</span>
          </article>
          <article class="editor-status-card">
            <strong>{{
              workspace.hasFreshServerResult.value ? 'Актуально' : 'Нужно обновить'
            }}</strong>
            <span>Состояние результата</span>
          </article>
        </div>
      </article>
    </section>

    <section class="editor-shell">
      <article class="panel-surface editor-main">
        <div class="editor-toolbar">
          <div class="editor-toolbar__cluster">
            <label class="editor-field">
              <span>Формат</span>
              <select v-model="workspace.selectedFormatId.value">
                <option
                  v-for="format in workspace.availableFormats.value"
                  :key="format.id"
                  :value="format.id"
                >
                  {{ format.label }}
                </option>
              </select>
            </label>

            <label class="editor-field">
              <span>Имя файла</span>
              <input v-model="workspace.fileName.value" type="text" spellcheck="false" />
            </label>

            <label class="editor-field">
              <span>Шаблон</span>
              <select v-model="workspace.selectedTemplateId.value" @change="onTemplateChange">
                <option
                  v-for="template in workspace.templateOptions.value"
                  :key="template.id"
                  :value="template.id"
                >
                  {{ template.label }}
                </option>
              </select>
            </label>
          </div>

          <div class="editor-toolbar__cluster editor-toolbar__cluster--actions">
            <button class="action-button" type="button" @click="triggerFileOpen">Открыть</button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.canFormat.value"
              @click="workspace.formatDocument"
            >
              Формат
            </button>
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="workspace.isValidating.value"
              @click="workspace.validateDocument()"
            >
              Проверить
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="workspace.isValidating.value"
              @click="workspace.downloadReadyFile"
            >
              Файл
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="workspace.isValidating.value"
              @click="workspace.downloadPlainTextFile"
            >
              Текст
            </button>
            <button
              v-if="workspace.activeJobId.value"
              class="action-button"
              type="button"
              :disabled="workspace.isCancelling.value"
              @click="workspace.cancelValidation"
            >
              Стоп
            </button>
            <button class="action-button" type="button" @click="workspace.clearPersistedDraft">
              Очистить
            </button>
            <input
              ref="fileInput"
              class="editor-hidden-input"
              type="file"
              :accept="workspace.editorAcceptAttribute.value"
              @change="onFileSelected"
            />
          </div>
        </div>

        <div class="editor-meta-row">
          <span class="chip-pill chip-pill--compact">{{
            workspace.draftPersistenceStatus.value
          }}</span>
          <span class="chip-pill chip-pill--compact">
            {{ workspace.preview.value.note ?? 'Предпросмотр содержимого' }}
          </span>
          <span
            v-if="workspace.restoredDraft.value"
            class="chip-pill chip-pill--compact chip-pill--accent"
          >
            Черновик восстановлен
          </span>
        </div>

        <div class="editor-helper-row" aria-label="Быстрые действия по формату">
          <button
            v-for="action in workspace.helperActions.value"
            :key="action.id"
            class="icon-button editor-helper"
            type="button"
            :title="action.detail"
            @click="onHelperClick(action.id)"
          >
            <span>{{ action.label }}</span>
            <small v-if="action.shortcut">{{ action.shortcut }}</small>
          </button>
        </div>

        <div v-if="workspace.processingMessage.value" class="editor-progress">
          <div class="editor-progress__track">
            <span
              class="editor-progress__fill"
              :style="{ width: `${workspace.activeJobProgressPercent.value}%` }"
            ></span>
          </div>
          <p>{{ workspace.processingMessage.value }}</p>
        </div>

        <p v-if="workspace.errorMessage.value" class="editor-error">
          {{ workspace.errorMessage.value }}
        </p>

        <div class="editor-surface">
          <pre ref="lineGutter" class="editor-gutter">{{ workspace.lineNumberGutter.value }}</pre>
          <textarea
            ref="textarea"
            v-model="workspace.content.value"
            class="editor-textarea"
            spellcheck="false"
            @keydown="onEditorKeydown"
            @scroll="onEditorScroll"
          ></textarea>
        </div>
      </article>

      <article class="panel-surface editor-side">
        <div class="editor-side__tabs" role="tablist" aria-label="Editor side panels">
          <button
            v-for="tab in sideTabs"
            :key="tab"
            class="icon-button editor-side__tab"
            :class="{ 'editor-side__tab--active': activeTab === tab }"
            type="button"
            @click="activeTab = tab"
          >
            {{ formatTabLabel(tab) }}
          </button>
        </div>

        <div v-if="activeTab === 'preview'" class="editor-side__panel editor-side__panel--preview">
          <p class="eyebrow">Просмотр</p>
          <iframe
            v-if="workspace.preview.value.mode === 'sandbox'"
            class="editor-preview-frame"
            :srcdoc="workspace.preview.value.html"
            sandbox="allow-same-origin"
          ></iframe>
          <div
            v-else-if="workspace.preview.value.mode === 'rich-html'"
            class="editor-rendered-preview"
            v-html="workspace.preview.value.html"
          ></div>
          <pre
            v-else-if="workspace.preview.value.mode === 'text'"
            class="editor-syntax-preview"
          ><code>{{ workspace.content.value }}</code></pre>
          <pre v-else class="editor-syntax-preview" v-html="workspace.preview.value.html"></pre>
        </div>

        <div v-else-if="activeTab === 'diagnostics'" class="editor-side__panel">
          <p class="eyebrow">Проверка</p>
          <p class="editor-side__note">
            {{
              workspace.hasFreshServerResult.value
                ? 'Показаны результаты для текущего текста.'
                : 'После изменений запусти проверку ещё раз, чтобы обновить замечания и структуру.'
            }}
          </p>

          <div v-if="workspace.diagnostics.value.length" class="editor-issue-list">
            <article
              v-for="issue in workspace.diagnostics.value"
              :key="`${issue.code}-${issue.line}-${issue.column}-${issue.message}`"
              class="editor-issue"
              :class="`editor-issue--${issue.severity}`"
            >
              <div class="editor-issue__topline">
                <span class="chip-pill chip-pill--compact">{{ issue.severity }}</span>
                <strong>{{ issue.code }}</strong>
                <span v-if="issue.line" class="editor-issue__position">
                  Строка {{ issue.line
                  }}<span v-if="issue.column"> · Колонка {{ issue.column }}</span>
                </span>
              </div>
              <p>{{ issue.message }}</p>
              <small v-if="issue.hint">{{ issue.hint }}</small>
            </article>
          </div>
          <p v-else class="editor-side__empty">
            Проверка ещё не запускалась или заметных проблем не найдено.
          </p>
        </div>

        <div v-else-if="activeTab === 'outline'" class="editor-side__panel">
          <p class="eyebrow">Структура</p>
          <p class="editor-side__note">
            Здесь собирается структура документа: сначала локальная, а после проверки более точная.
          </p>

          <div v-if="workspace.outlineItems.value.length" class="editor-outline">
            <div
              v-for="item in workspace.outlineItems.value"
              :key="item.id"
              class="editor-outline__item"
              :style="{ paddingInlineStart: `${12 + (item.depth - 1) * 18}px` }"
            >
              <strong>{{ item.label }}</strong>
              <span>{{ item.kind }}</span>
            </div>
          </div>
          <p v-else class="editor-side__empty">Для текущего текста структура пока не выделилась.</p>

          <div class="editor-suggestions">
            <span
              v-for="suggestion in workspace.suggestionPills.value"
              :key="suggestion"
              class="chip-pill chip-pill--compact"
            >
              {{ suggestion }}
            </span>
          </div>
        </div>

        <div v-else class="editor-side__panel">
          <p class="eyebrow">Экспорт</p>
          <div class="editor-facts">
            <article v-for="fact in summaryFacts" :key="fact.label" class="editor-fact">
              <span>{{ fact.label }}</span>
              <strong>{{ fact.value }}</strong>
            </article>
          </div>

          <div class="editor-export-actions">
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="workspace.isValidating.value"
              @click="workspace.validateDocument()"
            >
              Обновить проверку
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="workspace.isValidating.value"
              @click="workspace.downloadReadyFile"
            >
              Скачать файл
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="workspace.isValidating.value"
              @click="workspace.downloadPlainTextFile"
            >
              Скачать текст
            </button>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.editor-workspace {
  display: grid;
  gap: 22px;
}

.editor-topbar__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.editor-hero,
.editor-shell {
  display: grid;
  gap: 22px;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.95fr);
}

.editor-hero__copy,
.editor-hero__status,
.editor-main,
.editor-side {
  padding: 22px;
}

.editor-hero__copy h1 {
  margin: 0 0 14px;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 3.2rem);
  line-height: 1.02;
}

.lead {
  margin: 0;
  color: var(--text-soft);
  font-size: 1rem;
}

.editor-status-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.editor-status-card {
  display: grid;
  gap: 6px;
  padding: 18px;
  border-radius: 24px;
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.editor-status-card strong {
  color: var(--text-strong);
  font-size: 1.5rem;
  font-weight: 800;
}

.editor-status-card span {
  color: var(--text-soft);
  font-size: 0.85rem;
}

.editor-toolbar,
.editor-toolbar__cluster,
.editor-toolbar__cluster--actions,
.editor-meta-row,
.editor-helper-row,
.editor-export-actions,
.editor-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.editor-toolbar {
  justify-content: space-between;
  margin-bottom: 16px;
}

.editor-toolbar__cluster {
  align-items: flex-end;
}

.editor-toolbar__cluster--actions .action-button {
  min-width: 0;
  padding-inline: 14px;
}

.editor-field {
  display: grid;
  gap: 8px;
  min-width: 180px;
}

.editor-field span {
  color: var(--text-soft);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.editor-field select,
.editor-field input {
  min-height: 48px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 18px;
  background: rgba(255, 250, 243, 0.78);
  box-shadow: var(--shadow-pressed);
  color: var(--text-strong);
}

.editor-field select:focus-visible,
.editor-field input:focus-visible,
.editor-textarea:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.34);
  outline-offset: 3px;
}

.editor-hidden-input {
  display: none;
}

.editor-meta-row {
  margin-bottom: 14px;
}

.editor-helper-row {
  margin-bottom: 18px;
}

.editor-helper {
  display: grid;
  gap: 2px;
  min-width: 92px;
  padding-block: 11px;
}

.editor-helper small {
  color: var(--accent-coral);
  font-size: 0.7rem;
}

.editor-progress {
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 22px;
  background: rgba(255, 250, 242, 0.78);
  box-shadow: var(--shadow-pressed);
}

.editor-progress__track {
  overflow: hidden;
  height: 10px;
  margin-bottom: 10px;
  border-radius: 999px;
  background: rgba(29, 92, 85, 0.12);
}

.editor-progress__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent-coral), var(--accent-cool));
}

.editor-progress p,
.editor-error,
.editor-side__note,
.editor-side__empty {
  margin: 0;
  color: var(--text-soft);
}

.editor-error {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(243, 138, 85, 0.12);
  color: #8a4120;
}

.editor-surface {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  min-height: 620px;
  border-radius: 30px;
  background:
    linear-gradient(180deg, rgba(16, 36, 38, 0.92), rgba(20, 48, 45, 0.96)),
    radial-gradient(circle at top right, rgba(255, 207, 143, 0.08), transparent 22%);
  box-shadow: var(--shadow-floating);
}

.editor-gutter,
.editor-textarea,
.editor-syntax-preview {
  font-family:
    'SFMono-Regular', 'JetBrains Mono', 'Fira Code', 'IBM Plex Mono', 'Courier New', monospace;
}

.editor-gutter {
  overflow: hidden;
  margin: 0;
  padding: 24px 8px 24px 18px;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(255, 245, 232, 0.42);
  font-size: 0.82rem;
  line-height: 1.7;
  text-align: right;
  user-select: none;
}

.editor-textarea {
  width: 100%;
  min-height: 620px;
  padding: 24px 24px 24px 18px;
  border: 0;
  background: transparent;
  color: rgba(255, 245, 232, 0.92);
  font-size: 0.9rem;
  line-height: 1.7;
  resize: none;
}

.editor-side {
  display: grid;
  gap: 16px;
}

.editor-side__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.editor-side__tab {
  min-width: 108px;
  text-transform: capitalize;
}

.editor-side__tab--active {
  color: var(--accent-cool-strong);
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.64), transparent 42%),
    linear-gradient(145deg, rgba(255, 247, 236, 0.98), rgba(231, 220, 205, 0.96));
  box-shadow: var(--shadow-floating);
}

.editor-side__panel {
  display: grid;
  gap: 16px;
  min-height: 620px;
}

.editor-preview-frame {
  width: 100%;
  min-height: 560px;
  border: 0;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--shadow-pressed);
}

.editor-rendered-preview,
.editor-syntax-preview {
  overflow: auto;
  min-height: 560px;
  margin: 0;
  padding: 20px;
  border-radius: 26px;
  background: rgba(255, 250, 243, 0.84);
  box-shadow: var(--shadow-pressed);
}

.editor-rendered-preview {
  color: var(--text-main);
}

.editor-rendered-preview :deep(h1),
.editor-rendered-preview :deep(h2),
.editor-rendered-preview :deep(h3) {
  color: var(--text-strong);
  font-family: var(--font-display);
}

.editor-rendered-preview :deep(pre) {
  overflow: auto;
  padding: 14px;
  border-radius: 18px;
  background: rgba(16, 36, 38, 0.94);
  color: rgba(255, 250, 242, 0.9);
}

.editor-rendered-preview :deep(ul),
.editor-rendered-preview :deep(ol) {
  padding-left: 20px;
}

.editor-rendered-preview :deep(.task-list) {
  padding-left: 0;
  list-style: none;
}

.editor-rendered-preview :deep(.task-list__item) {
  margin: 0 0 10px;
}

.editor-rendered-preview :deep(.task-list__item label) {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.editor-rendered-preview :deep(.task-list__item input) {
  pointer-events: none;
}

.editor-syntax-preview {
  color: rgba(255, 245, 232, 0.92);
  background: linear-gradient(180deg, rgba(16, 36, 38, 0.92), rgba(20, 48, 45, 0.96));
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.editor-syntax-preview :deep(.token-keyword) {
  color: #ffcf8f;
}

.editor-syntax-preview :deep(.token-string),
.editor-rendered-preview :deep(.tree-string) {
  color: #f2a171;
}

.editor-syntax-preview :deep(.token-number),
.editor-rendered-preview :deep(.tree-value) {
  color: #8ed5c7;
}

.editor-syntax-preview :deep(.token-comment) {
  color: rgba(221, 214, 201, 0.5);
}

.editor-syntax-preview :deep(.token-key),
.editor-syntax-preview :deep(.token-heading) {
  color: #b4f3dc;
}

.editor-syntax-preview :deep(.token-code) {
  color: #e4d3bb;
}

.editor-syntax-preview :deep(.token-link) {
  color: #ffd5ac;
}

.editor-syntax-preview :deep(.token-tag) {
  color: #9fcfc7;
}

.editor-issue-list,
.editor-outline,
.editor-facts {
  display: grid;
  gap: 12px;
}

.editor-issue,
.editor-outline__item,
.editor-fact {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 250, 243, 0.82);
  box-shadow: var(--shadow-pressed);
}

.editor-issue__topline,
.editor-outline__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.editor-issue p,
.editor-issue small {
  margin: 0;
}

.editor-issue--error {
  border: 1px solid rgba(243, 138, 85, 0.3);
}

.editor-issue--warning {
  border: 1px solid rgba(255, 207, 143, 0.36);
}

.editor-outline__item span,
.editor-fact span,
.editor-issue__position {
  color: var(--text-soft);
  font-size: 0.82rem;
}

.editor-fact {
  display: grid;
  gap: 6px;
}

.editor-fact strong {
  color: var(--text-strong);
  font-size: 1rem;
}

.editor-export-actions {
  margin-top: auto;
}

@media (max-width: 1120px) {
  .editor-hero,
  .editor-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .editor-main,
  .editor-side,
  .editor-hero__copy,
  .editor-hero__status {
    padding: 18px;
  }

  .editor-surface {
    grid-template-columns: 44px minmax(0, 1fr);
    min-height: 520px;
  }

  .editor-textarea,
  .editor-gutter,
  .editor-side__panel,
  .editor-preview-frame,
  .editor-rendered-preview,
  .editor-syntax-preview {
    min-height: 460px;
  }
}
</style>
