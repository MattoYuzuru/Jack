import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { buildEditorLocalPreview, type EditorLocalPreview } from '../application/editor-preview'
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
  resolveEditorFormat,
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

interface PersistedEditorDraft {
  formatId: string
  fileName: string
  content: string
  templateId: string
}

const EDITOR_DRAFT_STORAGE_KEY = 'jack.editor.draft.v1'
const TEMPLATE_DEFINITIONS: EditorTemplateDefinition[] = [
  {
    id: 'markdown-brief',
    label: 'Markdown Brief',
    formatId: 'markdown',
    fileName: 'notes.md',
    content: `# Weekly Brief

## Highlights
- Viewer and converter polish
- Processing-platform reuse

## Checklist
- [ ] Validate final copy
- [ ] Publish update
`,
  },
  {
    id: 'html-landing',
    label: 'HTML Landing',
    formatId: 'html',
    fileName: 'landing.html',
    content: `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Jack Editor</title>
    <style>
      body { font-family: Manrope, sans-serif; padding: 32px; }
      main { max-width: 720px; margin: 0 auto; }
    </style>
  </head>
  <body>
    <main>
      <h1>Jack Editor</h1>
      <p>Structure your HTML draft and validate it with backend diagnostics.</p>
    </main>
  </body>
</html>
`,
  },
  {
    id: 'css-showcase',
    label: 'CSS Showcase',
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
    label: 'JS Worker',
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
    label: 'JSON Payload',
    formatId: 'json',
    fileName: 'payload.json',
    content: `{
  "module": "editor",
  "status": "active",
  "features": ["preview", "diagnostics", "export"]
}
`,
  },
  {
    id: 'yaml-config',
    label: 'YAML Config',
    formatId: 'yaml',
    fileName: 'editor-config.yaml',
    content: `editor:
  mode: split-view
  diagnostics: server
  exports:
    readyFile: true
    plainText: true
`,
  },
  {
    id: 'plain-notes',
    label: 'Plain Notes',
    formatId: 'txt',
    fileName: 'draft.txt',
    content: `SESSION:
Review editor roadmap coverage

NOTES:
- Keep formatting instant on frontend
- Push validate/export to backend
`,
  },
]

const HELPER_ACTIONS_BY_FORMAT: Record<string, EditorHelperAction[]> = {
  markdown: [
    { id: 'md-heading', label: 'Heading', detail: '# Section heading' },
    { id: 'md-bold', label: 'Bold', detail: 'Wrap selection with **bold**', shortcut: 'Mod+B' },
    { id: 'md-link', label: 'Link', detail: 'Insert markdown link', shortcut: 'Mod+K' },
    { id: 'md-code', label: 'Code Block', detail: 'Insert fenced code block' },
  ],
  html: [
    { id: 'html-section', label: 'Section', detail: 'Semantic section scaffold' },
    { id: 'html-card', label: 'Card', detail: 'Panel article snippet' },
    { id: 'html-link', label: 'Safe Link', detail: 'Anchor with noopener' },
    { id: 'html-list', label: 'List', detail: 'Bulleted list markup' },
  ],
  css: [
    { id: 'css-variables', label: 'Variables', detail: ':root variables block' },
    { id: 'css-flex', label: 'Flex Center', detail: 'Center content with flex' },
    { id: 'css-grid', label: 'Grid', detail: 'Responsive grid scaffold' },
    { id: 'css-media', label: 'Media Query', detail: 'Small-screen breakpoint block' },
  ],
  javascript: [
    { id: 'js-async', label: 'Async Fn', detail: 'Async function scaffold' },
    { id: 'js-fetch', label: 'Fetch Flow', detail: 'Fetch + error handling' },
    { id: 'js-try', label: 'Try / Catch', detail: 'Error boundary scaffold' },
    { id: 'js-listener', label: 'Listener', detail: 'Event listener snippet' },
  ],
  json: [
    { id: 'json-object', label: 'Object', detail: 'Wrap selection in object' },
    { id: 'json-array', label: 'Array', detail: 'Wrap selection in array' },
    { id: 'json-field', label: 'Field', detail: 'Insert key/value pair' },
  ],
  yaml: [
    { id: 'yaml-map', label: 'Mapping', detail: 'Key / nested mapping scaffold' },
    { id: 'yaml-list', label: 'List', detail: 'Sequence scaffold' },
    { id: 'yaml-service', label: 'Service', detail: 'Config block with enabled/port' },
  ],
  txt: [
    { id: 'txt-section', label: 'Section', detail: 'Uppercase section heading' },
    { id: 'txt-checklist', label: 'Checklist', detail: 'Checkbox note block' },
    { id: 'txt-divider', label: 'Divider', detail: 'Visual separator' },
  ],
}

function readPersistedDraft(): PersistedEditorDraft | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const rawValue = window.localStorage.getItem(EDITOR_DRAFT_STORAGE_KEY)
    if (!rawValue) {
      return null
    }

    const payload = JSON.parse(rawValue) as Partial<PersistedEditorDraft>
    if (
      typeof payload.formatId !== 'string' ||
      typeof payload.fileName !== 'string' ||
      typeof payload.content !== 'string'
    ) {
      return null
    }

    return {
      formatId: payload.formatId,
      fileName: payload.fileName,
      content: payload.content,
      templateId:
        typeof payload.templateId === 'string'
          ? payload.templateId
          : (TEMPLATE_DEFINITIONS[0]?.id ?? ''),
    }
  } catch {
    return null
  }
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

function normalizeSelectionContent(value: string): string {
  return value || 'text'
}

function insertText(
  textarea: HTMLTextAreaElement,
  nextValue: string,
  selectionStart: number,
  selectionEnd = selectionStart,
): void {
  textarea.value = nextValue
  textarea.setSelectionRange(selectionStart, selectionEnd)
  textarea.focus()
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
}

function wrapSelection(
  textarea: HTMLTextAreaElement,
  prefix: string,
  suffix: string,
  fallback = 'text',
): void {
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const currentValue = textarea.value
  const selection = currentValue.slice(start, end) || fallback
  const nextValue =
    currentValue.slice(0, start) + prefix + selection + suffix + currentValue.slice(end)
  const selectionStart = start + prefix.length
  const selectionEnd = selectionStart + selection.length

  insertText(textarea, nextValue, selectionStart, selectionEnd)
}

function replaceSelection(
  textarea: HTMLTextAreaElement,
  replacement: string,
  caretOffset = replacement.length,
): void {
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const currentValue = textarea.value
  const nextValue = currentValue.slice(0, start) + replacement + currentValue.slice(end)
  const nextCaret = start + caretOffset

  insertText(textarea, nextValue, nextCaret, nextCaret)
}

function indentSelection(textarea: HTMLTextAreaElement, outdent = false): void {
  const value = textarea.value
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const lineStart = value.lastIndexOf('\n', start - 1) + 1
  const lineEnd = value.indexOf('\n', end)
  const blockEnd = lineEnd === -1 ? value.length : lineEnd
  const selectedBlock = value.slice(lineStart, blockEnd)
  const lines = selectedBlock.split('\n')

  const transformedLines = lines.map((line) => {
    if (!outdent) {
      return `  ${line}`
    }

    return line.startsWith('  ') ? line.slice(2) : line.startsWith('\t') ? line.slice(1) : line
  })
  const nextBlock = transformedLines.join('\n')
  const nextValue = value.slice(0, lineStart) + nextBlock + value.slice(blockEnd)
  const delta = nextBlock.length - selectedBlock.length

  insertText(textarea, nextValue, start + (outdent ? -2 : 2), end + delta)
}

export function useEditorWorkspace() {
  const availableFormats = ref<EditorFormatDefinition[]>([])
  const editorAcceptAttribute = ref('.md,.html,.css,.js,.json,.yaml,.yml,.txt')
  const selectedFormatId = ref('markdown')
  const fileName = ref('notes.md')
  const content = ref(TEMPLATE_DEFINITIONS[0]?.content ?? '')
  const selectedTemplateId = ref(TEMPLATE_DEFINITIONS[0]?.id ?? '')
  const errorMessage = ref('')
  const processingMessage = ref('')
  const isHydrating = ref(false)
  const isValidating = ref(false)
  const isCancelling = ref(false)
  const activeJobId = ref('')
  const activeJobStatus = ref<ProcessingJobStatus | ''>('')
  const activeJobProgressPercent = ref(0)
  const draftPersistenceStatus = ref('Draft autosave idle')
  const lastValidatedFingerprint = ref('')
  const restoredDraft = ref(false)
  const lastValidatedResult = shallowRef<ServerEditorResult | null>(null)
  let capabilityRequest: Promise<void> | null = null
  let persistTimeoutId = 0

  const activeFormat = computed(
    () => availableFormats.value.find((format) => format.id === selectedFormatId.value) ?? null,
  )
  const preview = computed<EditorLocalPreview>(() =>
    buildEditorLocalPreview(selectedFormatId.value, content.value),
  )
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
  const templateOptions = computed(() => TEMPLATE_DEFINITIONS)
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

  function queueDraftPersistence(): void {
    if (typeof window === 'undefined') {
      return
    }

    window.clearTimeout(persistTimeoutId)
    persistTimeoutId = window.setTimeout(() => {
      window.localStorage.setItem(
        EDITOR_DRAFT_STORAGE_KEY,
        JSON.stringify({
          formatId: selectedFormatId.value,
          fileName: fileName.value,
          content: content.value,
          templateId: selectedTemplateId.value,
        }),
      )
      draftPersistenceStatus.value = 'Draft autosaved locally'
    }, 220)
  }

  function clearPersistedDraft(): void {
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(EDITOR_DRAFT_STORAGE_KEY)
    }
    draftPersistenceStatus.value = 'Draft cleared from local storage'
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

        const persistedDraft = readPersistedDraft()
        if (persistedDraft) {
          selectedFormatId.value = persistedDraft.formatId
          fileName.value = persistedDraft.fileName
          content.value = persistedDraft.content
          selectedTemplateId.value = persistedDraft.templateId
          restoredDraft.value = true
          draftPersistenceStatus.value = 'Restored local draft'
          return
        }

        applyTemplate(TEMPLATE_DEFINITIONS[0]?.id ?? '')
      })
      .finally(() => {
        isHydrating.value = false
        capabilityRequest = null
      })

    return capabilityRequest
  }

  function applyTemplate(templateId: string): void {
    const template = TEMPLATE_DEFINITIONS.find((entry) => entry.id === templateId)
    if (!template) {
      return
    }

    selectedTemplateId.value = template.id
    selectedFormatId.value = template.formatId
    fileName.value = template.fileName
    content.value = template.content
    lastValidatedResult.value = null
    lastValidatedFingerprint.value = ''
    queueDraftPersistence()
  }

  async function openFile(file: File): Promise<void> {
    errorMessage.value = ''
    const format = await resolveEditorFormat(file.name, file.type)

    if (!format?.available) {
      throw new Error(
        format?.availabilityDetail ||
          'Editor принимает только markdown, html, css, javascript, json, yaml и plain text drafts.',
      )
    }

    selectedFormatId.value = format.id
    fileName.value = file.name
    content.value = await file.text()
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
      draftPersistenceStatus.value = 'Draft formatted locally'
      queueDraftPersistence()
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось отформатировать текущий документ.'
    }
  }

  async function validateDocument(downloadMode: 'ready' | 'plain' | null = null): Promise<void> {
    if (!activeFormat.value) {
      return
    }

    errorMessage.value = ''
    isValidating.value = true
    processingMessage.value = 'Подготавливаю backend editor diagnostics...'

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

      lastValidatedResult.value = result
      lastValidatedFingerprint.value = currentFingerprint.value
      draftPersistenceStatus.value = 'Diagnostics synced with backend'
      processingMessage.value = ''

      if (downloadMode === 'ready') {
        downloadBlob(result.readyBlob, result.readyArtifact.fileName)
      }

      if (downloadMode === 'plain') {
        downloadBlob(result.plainTextBlob, result.plainTextArtifact.fileName)
      }
    } catch (error) {
      if (error instanceof ProcessingJobCancelledError) {
        errorMessage.value = 'Editor validate/export job был отменён.'
      } else {
        errorMessage.value =
          error instanceof Error
            ? error.message
            : 'Не удалось завершить editor validate/export flow.'
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
    processingMessage.value = 'Отменяю editor processing job...'

    try {
      await cancelProcessingJob(activeJobId.value)
      processingMessage.value = ''
      errorMessage.value = 'Editor processing job отменён.'
    } catch (error) {
      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось отменить editor processing job.'
    } finally {
      isCancelling.value = false
      activeJobId.value = ''
      activeJobStatus.value = ''
      activeJobProgressPercent.value = 0
    }
  }

  function buildDraftFile(): File {
    const fileType = activeFormat.value?.mimeTypes[0] || 'text/plain'
    return new File([content.value], fileName.value, {
      type: fileType,
    })
  }

  function applyHelperAction(actionId: string, textarea: HTMLTextAreaElement | null): void {
    if (!textarea) {
      return
    }

    switch (actionId) {
      case 'md-heading':
        replaceSelection(
          textarea,
          `## ${normalizeSelectionContent(textarea.value.slice(textarea.selectionStart, textarea.selectionEnd))}`,
        )
        return
      case 'md-bold':
        wrapSelection(textarea, '**', '**', 'bold')
        return
      case 'md-link':
        wrapSelection(textarea, '[', '](https://example.com)', 'label')
        return
      case 'md-code':
        replaceSelection(
          textarea,
          `\`\`\`ts\n${normalizeSelectionContent(textarea.value.slice(textarea.selectionStart, textarea.selectionEnd))}\n\`\`\``,
        )
        return
      case 'html-section':
        replaceSelection(
          textarea,
          `<section>\n  <h2>Section title</h2>\n  <p>Section copy</p>\n</section>`,
        )
        return
      case 'html-card':
        replaceSelection(
          textarea,
          `<article class="card">\n  <h3>Card title</h3>\n  <p>Supportive text.</p>\n</article>`,
        )
        return
      case 'html-link':
        replaceSelection(
          textarea,
          `<a href="https://example.com" target="_blank" rel="noopener noreferrer">Open link</a>`,
        )
        return
      case 'html-list':
        replaceSelection(textarea, `<ul>\n  <li>First item</li>\n  <li>Second item</li>\n</ul>`)
        return
      case 'css-variables':
        replaceSelection(textarea, `:root {\n  --accent: #1d5c55;\n  --panel: #fffaf2;\n}`)
        return
      case 'css-flex':
        replaceSelection(textarea, `display: flex;\nalign-items: center;\njustify-content: center;`)
        return
      case 'css-grid':
        replaceSelection(
          textarea,
          `display: grid;\ngrid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\ngap: 16px;`,
        )
        return
      case 'css-media':
        replaceSelection(
          textarea,
          `@media (max-width: 720px) {\n  .preview-grid {\n    grid-template-columns: 1fr;\n  }\n}`,
        )
        return
      case 'js-async':
        replaceSelection(
          textarea,
          `async function runTask() {\n  try {\n    return await Promise.resolve('done')\n  } catch (error) {\n    console.error(error)\n    throw error\n  }\n}`,
        )
        return
      case 'js-fetch':
        replaceSelection(
          textarea,
          `const response = await fetch('/api/example')\nif (!response.ok) {\n  throw new Error('Request failed')\n}\nconst payload = await response.json()`,
        )
        return
      case 'js-try':
        replaceSelection(textarea, `try {\n  // work\n} catch (error) {\n  console.error(error)\n}`)
        return
      case 'js-listener':
        replaceSelection(
          textarea,
          `window.addEventListener('click', (event) => {\n  console.log(event.type)\n})`,
        )
        return
      case 'json-object':
        replaceSelection(textarea, `{\n  "key": "value"\n}`)
        return
      case 'json-array':
        replaceSelection(textarea, `[\n  "item-1",\n  "item-2"\n]`)
        return
      case 'json-field':
        replaceSelection(textarea, `"key": "value"`)
        return
      case 'yaml-map':
        replaceSelection(textarea, `root:\n  key: value\n  nested:\n    enabled: true`)
        return
      case 'yaml-list':
        replaceSelection(textarea, `items:\n  - first\n  - second`)
        return
      case 'yaml-service':
        replaceSelection(textarea, `service:\n  enabled: true\n  port: 8080\n  host: localhost`)
        return
      case 'txt-section':
        replaceSelection(textarea, `SECTION:\n`)
        return
      case 'txt-checklist':
        replaceSelection(textarea, `- [ ] First task\n- [ ] Second task`)
        return
      case 'txt-divider':
        replaceSelection(textarea, `------------------------------`)
        return
    }
  }

  function handleEditorKeydown(event: KeyboardEvent, textarea: HTMLTextAreaElement | null): void {
    const isMod = event.metaKey || event.ctrlKey
    const normalizedKey = event.key.toLowerCase()

    if (isMod && normalizedKey === 's') {
      event.preventDefault()
      if (event.shiftKey) {
        void downloadPlainTextFile()
      } else {
        void downloadReadyFile()
      }
      return
    }

    if (isMod && normalizedKey === 'enter') {
      event.preventDefault()
      void validateDocument()
      return
    }

    if (event.altKey && event.shiftKey && normalizedKey === 'f') {
      event.preventDefault()
      void formatDocument()
      return
    }

    if (!textarea) {
      return
    }

    if (event.key === 'Tab') {
      event.preventDefault()
      indentSelection(textarea, event.shiftKey)
      return
    }

    if (selectedFormatId.value === 'markdown' && isMod && normalizedKey === 'b') {
      event.preventDefault()
      applyHelperAction('md-bold', textarea)
      return
    }

    if (selectedFormatId.value === 'markdown' && isMod && normalizedKey === 'k') {
      event.preventDefault()
      applyHelperAction('md-link', textarea)
    }
  }

  watch([selectedFormatId, fileName, content, selectedTemplateId], () => {
    if (capabilityRequest) {
      return
    }
    queueDraftPersistence()
  })

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
  })

  onBeforeUnmount(() => {
    window.clearTimeout(persistTimeoutId)
  })

  return {
    availableFormats,
    editorAcceptAttribute,
    selectedFormatId,
    fileName,
    content,
    selectedTemplateId,
    errorMessage,
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
    hydrateCapabilities,
    applyTemplate,
    openFile,
    formatDocument,
    validateDocument,
    downloadReadyFile,
    downloadPlainTextFile,
    cancelValidation,
    clearPersistedDraft,
    applyHelperAction,
    handleEditorKeydown,
  }
}
