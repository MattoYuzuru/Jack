import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { buildEditorLocalPreview, type EditorLocalPreview } from '../application/editor-preview'
import { renderMarkdownPreview } from '../application/markdown-preview-runtime'
import { decodeEditorFile, encodeEditorFile } from '../application/editor-file-codec'
import {
  clearEditorDraft,
  isEditorPersistenceEnabled,
  persistEditorDraft,
  readEditorDraft,
  setEditorPersistenceEnabled,
} from '../application/editor-persistence'
import { consumeEditorIncomingDraft } from '../application/editor-handoff'
import { canFormatEditorFormat, formatEditorContent } from '../application/editor-formatters'
import {
  runServerEditorProcess,
  type EditorIssue,
  type EditorOutlineItem,
  type ServerEditorResult,
} from '../application/editor-server-runtime'
import {
  getEditorAcceptAttribute,
  listEditorFormats,
  resolveEditorFormatMatch,
  type EditorFormatDefinition,
} from '../domain/editor-registry'
import {
  cancelProcessingJob,
  ProcessingJobCancelledError,
  type ProcessingJobStatus,
} from '../../processing/application/processing-client'

interface EditorTemplateDefinition {
  id: string
  label: string
  formatId: string
  fileName: string
  content: string
}

interface EditorHelperAction {
  id: string
  label: string
  detail: string
  shortcut?: string
}

const MAX_EDITOR_FILE_BYTES = 2 * 1024 * 1024
const TEMPLATE_DEFINITIONS: EditorTemplateDefinition[] = [
  {
    id: 'markdown-brief',
    label: 'План заметки',
    formatId: 'markdown',
    fileName: 'notes.md',
    content: `# План публикации

## Главное
- Коротко описать обновление
- Добавить пользу для пользователя

## Что проверить
- [ ] Финальный текст
- [ ] Ссылки и кнопки
- [ ] Готовность к публикации
`,
  },
  {
    id: 'html-landing',
    label: 'HTML Страница',
    formatId: 'html',
    fileName: 'landing.html',
    content: `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Jack Preview</title>
    <style>
      body { font-family: Manrope, sans-serif; padding: 32px; }
      main { max-width: 720px; margin: 0 auto; }
    </style>
  </head>
  <body>
    <main>
      <h1>Jack Preview</h1>
      <p>Собери простую HTML-страницу и сразу проверь, как она выглядит.</p>
    </main>
  </body>
</html>
`,
  },
  {
    id: 'css-showcase',
    label: 'CSS Сниппет',
    formatId: 'css',
    fileName: 'theme.css',
    content: `:root {
  --panel: rgba(255, 250, 242, 0.92);
  --accent: #1d5c55;
  --shadow: -12px -12px 24px rgba(255, 252, 245, 0.95), 16px 18px 32px rgba(93, 79, 60, 0.18);
}

.preview-card {
  border-radius: 24px;
  background: var(--panel);
  color: var(--accent);
  box-shadow: var(--shadow);
}
`,
  },
  {
    id: 'js-worker',
    label: 'JS Функция',
    formatId: 'javascript',
    fileName: 'workspace.js',
    content: `export async function loadWorkspaceSummary(endpoint) {
  const response = await fetch(endpoint)
  if (!response.ok) {
    throw new Error('Failed to load workspace summary')
  }

  return response.json()
}
`,
  },
  {
    id: 'json-payload',
    label: 'JSON Ответ',
    formatId: 'json',
    fileName: 'payload.json',
    content: `{
  "title": "Jack",
  "section": "editor",
  "published": true
}
`,
  },
  {
    id: 'yaml-config',
    label: 'YAML Конфиг',
    formatId: 'yaml',
    fileName: 'editor-config.yaml',
    content: `editor:
  title: Workspace
  autosave: true
  preview: split-view
`,
  },
  {
    id: 'plain-notes',
    label: 'Простые заметки',
    formatId: 'txt',
    fileName: 'draft.txt',
    content: `ЗАМЕТКИ:
Подготовить текст к публикации

ЧТО НУЖНО:
- проверить структуру
- вычитать текст
- сохранить итоговую версию
`,
  },
]

const HELPER_ACTIONS_BY_FORMAT: Record<string, EditorHelperAction[]> = {
  markdown: [
    { id: 'md-h1', label: 'H1', detail: 'Заголовок первого уровня' },
    { id: 'md-h2', label: 'H2', detail: 'Заголовок второго уровня' },
    { id: 'md-h3', label: 'H3', detail: 'Заголовок третьего уровня' },
    { id: 'md-h4', label: 'H4', detail: 'Заголовок четвёртого уровня' },
    { id: 'md-h5', label: 'H5', detail: 'Заголовок пятого уровня' },
    { id: 'md-h6', label: 'H6', detail: 'Заголовок шестого уровня' },
    { id: 'md-bold', label: 'Жирный', detail: 'Выделить текст жирным', shortcut: 'Mod+B' },
    { id: 'md-italic', label: 'Курсив', detail: 'Выделить текст курсивом' },
    { id: 'md-strike', label: 'Strike', detail: 'Зачеркнуть текст' },
    { id: 'md-inline-code', label: 'Inline code', detail: 'Выделить фрагмент кода' },
    { id: 'md-link', label: 'Ссылка', detail: 'Вставить markdown-ссылку', shortcut: 'Mod+K' },
    { id: 'md-image', label: 'Изображение', detail: 'Вставить image syntax' },
    { id: 'md-code', label: 'Code block', detail: 'Вставить блок кода' },
    { id: 'md-quote', label: 'Цитата', detail: 'Добавить blockquote' },
    { id: 'md-bullet', label: 'Список', detail: 'Добавить маркированный список' },
    { id: 'md-ordered', label: '1. Список', detail: 'Добавить нумерованный список' },
    { id: 'md-task', label: 'Задача', detail: 'Добавить task item' },
    { id: 'md-table', label: 'Таблица', detail: 'Вставить GFM table' },
    { id: 'md-hr', label: 'Разделитель', detail: 'Вставить thematic break' },
    { id: 'md-footnote', label: 'Сноска', detail: 'Вставить footnote' },
  ],
  html: [
    { id: 'html-section', label: 'Секция', detail: 'Добавить базовый section-блок' },
    { id: 'html-card', label: 'Карточка', detail: 'Вставить компактный блок-карточку' },
    { id: 'html-link', label: 'Ссылка', detail: 'Безопасная внешняя ссылка' },
    { id: 'html-list', label: 'Список', detail: 'Маркированный список' },
    { id: 'html-table', label: 'Таблица', detail: 'Таблица с caption и scope' },
    { id: 'html-form', label: 'Форма', detail: 'Форма с доступной подписью' },
    { id: 'html-landmarks', label: 'Landmarks', detail: 'Семантический каркас страницы' },
  ],
  css: [
    { id: 'css-variables', label: 'Переменные', detail: 'Блок переменных в :root' },
    { id: 'css-flex', label: 'Flex', detail: 'Центрирование через flex' },
    { id: 'css-grid', label: 'Grid', detail: 'Адаптивная сетка' },
    { id: 'css-media', label: 'Media', detail: 'Правило для маленьких экранов' },
    { id: 'css-rule', label: 'Rule', detail: 'Обычное CSS-правило' },
    { id: 'css-container', label: 'Container', detail: 'Container query' },
    { id: 'css-keyframes', label: 'Keyframes', detail: 'Анимация с keyframes' },
  ],
  javascript: [
    { id: 'js-async', label: 'Async', detail: 'Асинхронная функция' },
    { id: 'js-fetch', label: 'Fetch', detail: 'Запрос с обработкой ошибки' },
    { id: 'js-try', label: 'Try/Catch', detail: 'Блок обработки ошибок' },
    { id: 'js-listener', label: 'Listener', detail: 'Обработчик события' },
    { id: 'js-module', label: 'Module', detail: 'Import/export блок' },
    { id: 'js-function', label: 'Function', detail: 'Экспортируемая функция' },
  ],
  json: [
    { id: 'json-object', label: 'Object', detail: 'Добавить объект JSON' },
    { id: 'json-array', label: 'Array', detail: 'Добавить массив JSON' },
    { id: 'json-field', label: 'Field', detail: 'Вставить пару ключ-значение' },
  ],
  yaml: [
    { id: 'yaml-map', label: 'Mapping', detail: 'Карта с вложенными полями' },
    { id: 'yaml-list', label: 'List', detail: 'Список значений' },
    { id: 'yaml-service', label: 'Config', detail: 'Готовый блок конфигурации' },
  ],
  txt: [
    { id: 'txt-section', label: 'Раздел', detail: 'Заголовок раздела' },
    { id: 'txt-checklist', label: 'Чеклист', detail: 'Список задач с чекбоксами' },
    { id: 'txt-divider', label: 'Разделитель', detail: 'Горизонтальный разделитель' },
  ],
}

function downloadBlob(blob: Blob, fileName: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  anchor.click()
  window.setTimeout(() => {
    URL.revokeObjectURL(objectUrl)
  }, 0)
}

function countWords(content: string): number {
  const normalized = content.trim()
  if (!normalized) {
    return 0
  }

  return normalized.split(/\s+/u).filter(Boolean).length
}

export function useEditorWorkspace() {
  const availableFormats = ref<EditorFormatDefinition[]>([])
  const editorAcceptAttribute = ref('.md,.html,.css,.js,.json,.yaml,.yml,.txt')
  const selectedFormatId = ref('markdown')
  const fileName = ref('notes.md')
  const content = ref(TEMPLATE_DEFINITIONS[0]?.content ?? '')
  const selectedTemplateId = ref(TEMPLATE_DEFINITIONS[0]?.id ?? '')
  const encoding = ref<'utf-8' | 'utf-8-bom'>('utf-8')
  const newline = ref<'lf' | 'crlf'>('lf')
  const persistenceEnabled = ref(
    typeof window !== 'undefined' && window.localStorage
      ? isEditorPersistenceEnabled(window.localStorage)
      : false,
  )
  const errorMessage = ref('')
  const formatMismatchWarning = ref('')
  const processingMessage = ref('')
  const isHydrating = ref(false)
  const isValidating = ref(false)
  const isCancelling = ref(false)
  const activeJobId = ref('')
  const activeJobStatus = ref<ProcessingJobStatus | ''>('')
  const activeJobProgressPercent = ref(0)
  const draftPersistenceStatus = ref('Черновик готов к работе')
  const lastValidatedFingerprint = ref('')
  const restoredDraft = ref(false)
  const lastValidatedResult = shallowRef<ServerEditorResult | null>(null)
  let capabilityRequest: Promise<void> | null = null
  let persistTimeoutId = 0
  let markdownPreviewTimeoutId = 0
  let markdownPreviewRevision = 0
  let validationRevision = 0
  let markdownPreviewController: AbortController | null = null
  let lastAppliedTemplateId = selectedTemplateId.value
  const serverMarkdownPreview = shallowRef<EditorLocalPreview | null>(null)

  const activeFormat = computed(
    () => availableFormats.value.find((format) => format.id === selectedFormatId.value) ?? null,
  )
  const preview = computed<EditorLocalPreview>(() => {
    if (selectedFormatId.value === 'markdown' && serverMarkdownPreview.value) {
      return serverMarkdownPreview.value
    }

    return buildEditorLocalPreview(selectedFormatId.value, content.value)
  })
  const lineCount = computed(() => (content.value ? content.value.split('\n').length : 1))
  const lineNumberGutter = computed(() =>
    Array.from({ length: lineCount.value }, (_unused, index) => String(index + 1)).join('\n'),
  )
  const characterCount = computed(() => content.value.length)
  const wordCount = computed(() => countWords(content.value))
  const canFormat = computed(
    () => !!activeFormat.value?.supportsFormatting && canFormatEditorFormat(selectedFormatId.value),
  )
  const helperActions = computed<EditorHelperAction[]>(
    () => HELPER_ACTIONS_BY_FORMAT[selectedFormatId.value] ?? [],
  )
  const templateOptions = computed(() =>
    TEMPLATE_DEFINITIONS.filter((template) => template.formatId === selectedFormatId.value),
  )
  const currentFingerprint = computed(
    () => `${selectedFormatId.value}\u0000${fileName.value}\u0000${content.value}`,
  )
  const hasServerResult = computed(() => !!lastValidatedResult.value)
  const hasFreshServerResult = computed(
    () =>
      !!lastValidatedResult.value && lastValidatedFingerprint.value === currentFingerprint.value,
  )
  const diagnostics = computed<EditorIssue[]>(
    () => lastValidatedResult.value?.manifest.issues ?? [],
  )
  const outlineItems = computed<EditorOutlineItem[]>(() => {
    if (hasFreshServerResult.value && lastValidatedResult.value?.manifest.outline.length) {
      return lastValidatedResult.value.manifest.outline
    }

    return preview.value.outline
  })
  const serverSummary = computed(() => lastValidatedResult.value?.manifest.summary ?? [])
  const suggestionPills = computed<string[]>(() => {
    if (hasFreshServerResult.value && lastValidatedResult.value) {
      return lastValidatedResult.value.manifest.suggestions
    }

    return activeFormat.value?.accents ?? []
  })
  const diagnosticsBySeverity = computed(() => ({
    error: diagnostics.value.filter((issue) => issue.severity === 'error').length,
    warning: diagnostics.value.filter((issue) => issue.severity === 'warning').length,
    info: diagnostics.value.filter((issue) => issue.severity === 'info').length,
  }))
  const diagnosticsScope = computed(() =>
    ['json', 'yaml'].includes(selectedFormatId.value)
      ? 'Parser-backed diagnostics с точной позицией ошибки.'
      : ['css', 'javascript'].includes(selectedFormatId.value)
        ? 'Structural preflight: это не полная runtime-валидация.'
        : selectedFormatId.value === 'html'
          ? 'HTML parse и security preflight; browser-runtime не запускается.'
          : 'Server diagnostics для текущего формата.',
  )

  function queueDraftPersistence(): void {
    if (typeof window === 'undefined') {
      return
    }

    window.clearTimeout(persistTimeoutId)
    if (!persistenceEnabled.value) {
      clearEditorDraft(window.localStorage)
      draftPersistenceStatus.value = 'Локальное восстановление выключено'
      return
    }

    persistTimeoutId = window.setTimeout(() => {
      const result = persistEditorDraft(window.localStorage, {
        version: 2,
        formatId: selectedFormatId.value,
        fileName: fileName.value,
        content: content.value,
        templateId: selectedTemplateId.value,
        encoding: encoding.value,
        newline: newline.value,
      })
      draftPersistenceStatus.value =
        result.status === 'saved'
          ? 'Recovery snapshot сохранён локально'
          : result.status === 'too-large'
            ? 'Черновик больше лимита 512 KiB и не сохранён'
            : result.status === 'quota-error'
              ? 'Браузер отказал в сохранении: квота исчерпана'
              : 'Локальное восстановление выключено'
    }, 500)
  }

  function clearPersistedDraft(): void {
    if (typeof window !== 'undefined') {
      clearEditorDraft(window.localStorage)
    }
    draftPersistenceStatus.value = 'Локальная копия очищена'
  }

  async function hydrateCapabilities(): Promise<void> {
    if (capabilityRequest) {
      return capabilityRequest
    }

    isHydrating.value = true
    capabilityRequest = Promise.all([getEditorAcceptAttribute(), listEditorFormats()])
      .then(([acceptAttribute, formats]) => {
        editorAcceptAttribute.value = acceptAttribute
        availableFormats.value = formats.filter((format) => format.available)
        if (!availableFormats.value.some((format) => format.id === selectedFormatId.value)) {
          selectedFormatId.value = availableFormats.value[0]?.id ?? 'markdown'
        }

        const incomingDraft = consumeEditorIncomingDraft()
        if (incomingDraft) {
          selectedFormatId.value = availableFormats.value.some(
            (format) => format.id === incomingDraft.formatId,
          )
            ? incomingDraft.formatId
            : 'txt'
          fileName.value = incomingDraft.fileName
          content.value = incomingDraft.content
          selectedTemplateId.value = ''
          restoredDraft.value = false
          draftPersistenceStatus.value = `Открыт черновик из ${incomingDraft.sourceLabel}`
          lastValidatedResult.value = null
          lastValidatedFingerprint.value = ''
          queueDraftPersistence()
          return
        }

        const persistedDraft = readEditorDraft(window.localStorage)
        if (persistedDraft) {
          selectedFormatId.value = persistedDraft.formatId
          fileName.value = persistedDraft.fileName
          content.value = persistedDraft.content
          selectedTemplateId.value = persistedDraft.templateId
          encoding.value = persistedDraft.encoding
          newline.value = persistedDraft.newline
          restoredDraft.value = true
          draftPersistenceStatus.value = 'Локальная копия восстановлена'
          return
        }

        applyTemplate(TEMPLATE_DEFINITIONS[0]?.id ?? '', false)
      })
      .finally(() => {
        isHydrating.value = false
        capabilityRequest = null
      })

    return capabilityRequest
  }

  function applyTemplate(templateId: string, requireConfirmation = true): void {
    const template = TEMPLATE_DEFINITIONS.find((entry) => entry.id === templateId)
    if (!template) {
      return
    }

    const previousTemplate = TEMPLATE_DEFINITIONS.find(
      (entry) => entry.id === lastAppliedTemplateId,
    )
    const isDirty =
      !previousTemplate ||
      content.value !== previousTemplate.content ||
      fileName.value !== previousTemplate.fileName
    if (
      requireConfirmation &&
      isDirty &&
      !window.confirm('Заменить несохранённый черновик выбранным шаблоном?')
    ) {
      selectedTemplateId.value = lastAppliedTemplateId
      return
    }

    selectedTemplateId.value = template.id
    lastAppliedTemplateId = template.id
    selectedFormatId.value = template.formatId
    fileName.value = template.fileName
    content.value = template.content
    lastValidatedResult.value = null
    lastValidatedFingerprint.value = ''
    queueDraftPersistence()
  }

  async function openFile(file: File): Promise<void> {
    errorMessage.value = ''
    formatMismatchWarning.value = ''
    if (file.size > MAX_EDITOR_FILE_BYTES) {
      throw new Error('Editor открывает локальные файлы размером не более 2 MiB.')
    }
    const resolution = await resolveEditorFormatMatch(file.name, file.type)
    const format = resolution.format

    if (!format?.available) {
      throw new Error(
        format?.availabilityDetail ||
          'Editor принимает только markdown, html, css, javascript, json, yaml и plain text drafts.',
      )
    }

    selectedFormatId.value = format.id
    formatMismatchWarning.value = resolution.mismatchWarning ?? ''
    fileName.value = file.name
    const decoded = decodeEditorFile(await file.arrayBuffer())
    encoding.value = decoded.encoding
    newline.value = decoded.newline
    content.value = decoded.content
    selectedTemplateId.value = ''
    lastAppliedTemplateId = ''
    lastValidatedResult.value = null
    lastValidatedFingerprint.value = ''
    queueDraftPersistence()
  }

  async function formatDocument(): Promise<void> {
    if (!canFormat.value) {
      return
    }

    errorMessage.value = ''
    try {
      content.value = await formatEditorContent(selectedFormatId.value, content.value)
      draftPersistenceStatus.value = 'Черновик отформатирован'
      queueDraftPersistence()
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось отформатировать текущий документ.'
    }
  }

  async function validateDocument(downloadMode: 'ready' | 'plain' | null = null): Promise<void> {
    if (!activeFormat.value || isValidating.value) {
      return
    }

    const requestRevision = ++validationRevision
    const requestFingerprint = currentFingerprint.value
    errorMessage.value = ''
    isValidating.value = true
    processingMessage.value = 'Проверяю документ и собираю итоговые файлы...'

    try {
      const result = await runServerEditorProcess({
        file: buildDraftFile(),
        formatId: activeFormat.value.id,
        reportProgress: (message) => {
          processingMessage.value = message
        },
        onJobCreated: (job) => {
          activeJobId.value = job.id
          activeJobStatus.value = job.status
          activeJobProgressPercent.value = job.progressPercent
        },
        onJobUpdate: (job) => {
          activeJobStatus.value = job.status
          activeJobProgressPercent.value = job.progressPercent
        },
      })

      // Ответ для уже изменённого draft не должен вытеснить актуальную локальную ревизию.
      if (
        requestRevision !== validationRevision ||
        requestFingerprint !== currentFingerprint.value
      ) {
        draftPersistenceStatus.value = 'Текст изменился во время проверки — результат отброшен'
        return
      }

      lastValidatedResult.value = result
      lastValidatedFingerprint.value = requestFingerprint
      draftPersistenceStatus.value = 'Проверка завершена, результат актуален'
      processingMessage.value = ''

      if (downloadMode === 'ready') {
        downloadBlob(result.readyBlob, result.readyArtifact.fileName)
      }

      if (downloadMode === 'plain') {
        downloadBlob(result.plainTextBlob, result.plainTextArtifact.fileName)
      }
    } catch (error) {
      if (error instanceof ProcessingJobCancelledError) {
        errorMessage.value = 'Проверка документа была отменена.'
      } else {
        errorMessage.value =
          error instanceof Error
            ? error.message
            : 'Не удалось завершить проверку и подготовку файлов.'
      }
    } finally {
      isValidating.value = false
      isCancelling.value = false
      activeJobId.value = ''
      activeJobStatus.value = ''
      activeJobProgressPercent.value = 0
      if (!errorMessage.value) {
        processingMessage.value = ''
      }
    }
  }

  async function downloadReadyFile(): Promise<void> {
    if (hasFreshServerResult.value && lastValidatedResult.value) {
      downloadBlob(
        lastValidatedResult.value.readyBlob,
        lastValidatedResult.value.readyArtifact.fileName,
      )
      return
    }

    await validateDocument('ready')
  }

  async function downloadPlainTextFile(): Promise<void> {
    if (hasFreshServerResult.value && lastValidatedResult.value) {
      downloadBlob(
        lastValidatedResult.value.plainTextBlob,
        lastValidatedResult.value.plainTextArtifact.fileName,
      )
      return
    }

    await validateDocument('plain')
  }

  async function cancelValidation(): Promise<void> {
    if (!activeJobId.value || isCancelling.value) {
      return
    }

    isCancelling.value = true
    processingMessage.value = 'Останавливаю текущую проверку...'

    try {
      await cancelProcessingJob(activeJobId.value)
      processingMessage.value = ''
      errorMessage.value = 'Проверка документа отменена.'
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось отменить текущую проверку.'
    } finally {
      isCancelling.value = false
      activeJobId.value = ''
      activeJobStatus.value = ''
      activeJobProgressPercent.value = 0
    }
  }

  function buildDraftFile(): File {
    const fileType = activeFormat.value?.mimeTypes[0] || 'text/plain'
    return new File(
      [encodeEditorFile(content.value, encoding.value, newline.value)],
      fileName.value,
      {
        type: fileType,
      },
    )
  }

  watch([selectedFormatId, fileName, content, selectedTemplateId], () => {
    if (capabilityRequest) {
      return
    }
    queueDraftPersistence()
  })

  watch(
    [selectedFormatId, content],
    ([formatId, source]) => {
      window.clearTimeout(markdownPreviewTimeoutId)
      markdownPreviewController?.abort()
      markdownPreviewController = null
      const revision = ++markdownPreviewRevision

      if (formatId !== 'markdown') {
        serverMarkdownPreview.value = null
        return
      }

      markdownPreviewTimeoutId = window.setTimeout(() => {
        const controller = new AbortController()
        markdownPreviewController = controller

        void renderMarkdownPreview(source, controller.signal)
          .then((nextPreview) => {
            if (revision === markdownPreviewRevision) {
              serverMarkdownPreview.value = nextPreview
            }
          })
          .catch(() => {
            // При offline/stale request сохраняем inert fallback и не заменяем новый draft.
          })
          .finally(() => {
            if (revision === markdownPreviewRevision) {
              markdownPreviewController = null
            }
          })
      }, 280)
    },
    { immediate: true },
  )

  watch(selectedFormatId, (nextFormatId) => {
    const currentFormat = availableFormats.value.find((format) => format.id === nextFormatId)
    if (!currentFormat) {
      return
    }

    const baseName = fileName.value.replace(/\.[^.]+$/u, '')
    const currentExtension = fileName.value.includes('.')
      ? fileName.value.slice(fileName.value.lastIndexOf('.') + 1).toLowerCase()
      : ''

    if (!currentFormat.extensions.includes(currentExtension)) {
      fileName.value = `${baseName || 'untitled'}.${currentFormat.extensions[0]}`
    }

    const currentTemplateIsCompatible = templateOptions.value.some(
      (template) => template.id === selectedTemplateId.value,
    )
    if (!currentTemplateIsCompatible) {
      selectedTemplateId.value = ''
      lastAppliedTemplateId = ''
    }
  })

  watch(persistenceEnabled, (enabled) => {
    setEditorPersistenceEnabled(window.localStorage, enabled)
    queueDraftPersistence()
  })

  onBeforeUnmount(() => {
    window.clearTimeout(persistTimeoutId)
    window.clearTimeout(markdownPreviewTimeoutId)
    markdownPreviewController?.abort()
  })

  return {
    availableFormats,
    editorAcceptAttribute,
    selectedFormatId,
    fileName,
    content,
    selectedTemplateId,
    encoding,
    newline,
    persistenceEnabled,
    errorMessage,
    formatMismatchWarning,
    processingMessage,
    isHydrating,
    isValidating,
    isCancelling,
    activeJobId,
    activeJobStatus,
    activeJobProgressPercent,
    draftPersistenceStatus,
    restoredDraft,
    activeFormat,
    preview,
    lineCount,
    lineNumberGutter,
    characterCount,
    wordCount,
    canFormat,
    helperActions,
    templateOptions,
    hasServerResult,
    hasFreshServerResult,
    diagnostics,
    outlineItems,
    serverSummary,
    suggestionPills,
    diagnosticsBySeverity,
    diagnosticsScope,
    hydrateCapabilities,
    applyTemplate,
    openFile,
    formatDocument,
    validateDocument,
    downloadReadyFile,
    downloadPlainTextFile,
    cancelValidation,
    clearPersistedDraft,
  }
}
