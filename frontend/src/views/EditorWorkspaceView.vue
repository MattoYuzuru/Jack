<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import DiagnosticsPanel from '../components/editor/DiagnosticsPanel.vue'
import EditorSurface from '../components/editor/EditorSurface.vue'
import ExportPanel from '../components/editor/ExportPanel.vue'
import FormatToolbar from '../components/editor/FormatToolbar.vue'
import OutlinePanel from '../components/editor/OutlinePanel.vue'
import PreviewPanel from '../components/editor/PreviewPanel.vue'
import { useEditorWorkspace } from '../features/editor/composables/useEditorWorkspace'

type EditorPanelTab = 'preview' | 'diagnostics' | 'outline' | 'exports'

const workspace = useEditorWorkspace()
const activeTab = ref<EditorPanelTab>('preview')
const sideTabs: EditorPanelTab[] = ['preview', 'diagnostics', 'outline', 'exports']
const editorSurface = ref<{
  applyCommand: (commandId: string) => boolean
  runUndo: () => boolean
  runRedo: () => boolean
  focus: () => void
} | null>(null)

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

async function onFileSelected(file: File): Promise<void> {
  try {
    await workspace.openFile(file)
  } catch (error) {
    workspace.errorMessage.value =
      error instanceof Error ? error.message : 'Не удалось открыть выбранный файл.'
  }
}

function onHelperClick(actionId: string): void {
  editorSurface.value?.applyCommand(actionId)
}

function onToolbarAction(
  action: 'open' | 'undo' | 'redo' | 'format' | 'validate' | 'ready' | 'text' | 'cancel' | 'clear',
): void {
  switch (action) {
    case 'open':
      return
    case 'undo':
      editorSurface.value?.runUndo()
      return
    case 'redo':
      editorSurface.value?.runRedo()
      return
    case 'format':
      void workspace.formatDocument()
      return
    case 'validate':
      void workspace.validateDocument()
      return
    case 'ready':
      void workspace.downloadReadyFile()
      return
    case 'text':
      void workspace.downloadPlainTextFile()
      return
    case 'cancel':
      void workspace.cancelValidation()
      return
    case 'clear':
      workspace.clearPersistedDraft()
  }
}

function onEditorShortcut(action: 'save' | 'save-text' | 'validate' | 'format'): void {
  switch (action) {
    case 'save':
      void workspace.downloadReadyFile()
      return
    case 'save-text':
      void workspace.downloadPlainTextFile()
      return
    case 'validate':
      void workspace.validateDocument()
      return
    case 'format':
      void workspace.formatDocument()
  }
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
        <FormatToolbar
          :formats="workspace.availableFormats.value"
          :format-id="workspace.selectedFormatId.value"
          :file-name="workspace.fileName.value"
          :encoding="workspace.encoding.value"
          :newline="workspace.newline.value"
          :persistence-enabled="workspace.persistenceEnabled.value"
          :templates="workspace.templateOptions.value"
          :selected-template-id="workspace.selectedTemplateId.value"
          :current-content="workspace.content.value"
          :helper-actions="workspace.helperActions.value"
          :can-format="workspace.canFormat.value"
          :busy="workspace.isValidating.value"
          :active-job="Boolean(workspace.activeJobId.value)"
          :cancelling="workspace.isCancelling.value"
          :accept="workspace.editorAcceptAttribute.value"
          @update:format-id="workspace.selectedFormatId.value = $event"
          @update:file-name="workspace.fileName.value = $event"
          @update:encoding="workspace.encoding.value = $event"
          @update:newline="workspace.newline.value = $event"
          @update:persistence-enabled="workspace.persistenceEnabled.value = $event"
          @template="workspace.applyTemplate($event)"
          @command="onHelperClick"
          @action="onToolbarAction"
          @file="onFileSelected"
        />

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

        <p v-if="workspace.formatMismatchWarning.value" class="editor-warning" role="status">
          {{ workspace.formatMismatchWarning.value }}
        </p>

        <div class="editor-surface">
          <EditorSurface
            ref="editorSurface"
            v-model="workspace.content.value"
            :format-id="workspace.selectedFormatId.value"
            @shortcut="onEditorShortcut"
          />
        </div>
      </article>

      <article class="panel-surface editor-side">
        <div class="editor-side__tabs" role="tablist" aria-label="Editor side panels">
          <button
            v-for="tab in sideTabs"
            :key="tab"
            :id="`editor-tab-${tab}`"
            class="icon-button editor-side__tab"
            :class="{ 'editor-side__tab--active': activeTab === tab }"
            type="button"
            role="tab"
            :aria-selected="activeTab === tab"
            :aria-controls="`editor-panel-${tab}`"
            :tabindex="activeTab === tab ? 0 : -1"
            @click="activeTab = tab"
          >
            {{ formatTabLabel(tab) }}
          </button>
        </div>

        <div
          v-if="activeTab === 'preview'"
          id="editor-panel-preview"
          class="editor-side__panel editor-side__panel--preview"
          role="tabpanel"
          aria-labelledby="editor-tab-preview"
          tabindex="0"
        >
          <PreviewPanel :preview="workspace.preview.value" :content="workspace.content.value" />
        </div>

        <div
          v-else-if="activeTab === 'diagnostics'"
          id="editor-panel-diagnostics"
          class="editor-side__panel"
          role="tabpanel"
          aria-labelledby="editor-tab-diagnostics"
          tabindex="0"
        >
          <DiagnosticsPanel
            :issues="workspace.diagnostics.value"
            :fresh="workspace.hasFreshServerResult.value"
            :scope-label="workspace.diagnosticsScope.value"
          />
        </div>

        <div
          v-else-if="activeTab === 'outline'"
          id="editor-panel-outline"
          class="editor-side__panel"
          role="tabpanel"
          aria-labelledby="editor-tab-outline"
          tabindex="0"
        >
          <OutlinePanel
            :items="workspace.outlineItems.value"
            :suggestions="workspace.suggestionPills.value"
          />
        </div>

        <div
          v-else
          id="editor-panel-exports"
          class="editor-side__panel"
          role="tabpanel"
          aria-labelledby="editor-tab-exports"
          tabindex="0"
        >
          <ExportPanel
            :facts="summaryFacts"
            :busy="workspace.isValidating.value"
            @validate="workspace.validateDocument()"
            @download-ready="workspace.downloadReadyFile"
            @download-text="workspace.downloadPlainTextFile"
          />
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
.editor-field input:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.34);
  outline-offset: 3px;
}

.editor-field--checkbox {
  display: flex;
  min-width: 0;
  align-items: center;
  padding-bottom: 8px;
}

.editor-field--checkbox input {
  width: 24px;
  min-height: 24px;
  padding: 0;
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

.editor-warning {
  margin: 0 0 16px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 207, 143, 0.18);
  color: #72491d;
}

.editor-surface {
  display: grid;
  min-width: 0;
  min-height: 32rem;
  overflow: hidden;
  border-radius: 30px;
  background: rgba(255, 250, 242, 0.72);
  box-shadow: var(--shadow-floating);
}

.editor-syntax-preview {
  font-family:
    'SFMono-Regular', 'JetBrains Mono', 'Fira Code', 'IBM Plex Mono', 'Courier New', monospace;
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
    min-height: 26rem;
  }

  .editor-side__panel,
  .editor-preview-frame,
  .editor-rendered-preview,
  .editor-syntax-preview {
    min-height: 460px;
  }
}
</style>
