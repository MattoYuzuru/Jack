<script setup lang="ts">
import { basicSetup } from 'codemirror'
import { Compartment, EditorState, Prec, type Extension } from '@codemirror/state'
import { indentLess, indentMore, redo, undo } from '@codemirror/commands'
import { EditorView, keymap } from '@codemirror/view'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  applyEditorCommand,
  continueMarkdownList,
} from '../../features/editor/application/editor-commands'

const props = defineProps<{
  modelValue: string
  formatId: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  shortcut: [action: 'save' | 'save-text' | 'validate' | 'format']
}>()

const host = ref<HTMLElement | null>(null)
const language = new Compartment()
let view: EditorView | null = null
let languageRevision = 0
let applyingExternalValue = false

onMounted(() => {
  view = new EditorView({
    parent: host.value!,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        basicSetup,
        EditorState.allowMultipleSelections.of(true),
        language.of([]),
        editorTheme,
        EditorView.contentAttributes.of({
          'aria-label': 'Содержимое документа',
          spellcheck: 'false',
        }),
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !applyingExternalValue) {
            emit('update:modelValue', update.state.doc.toString())
          }
        }),
        Prec.highest(
          keymap.of([
            { key: 'Mod-b', run: (target) => applyEditorCommand(target, 'md-bold') },
            { key: 'Mod-k', run: (target) => applyEditorCommand(target, 'md-link') },
            {
              key: 'Enter',
              run: (target) => props.formatId === 'markdown' && continueMarkdownList(target),
            },
            { key: 'Tab', run: indentMore },
            { key: 'Shift-Tab', run: indentLess },
            { key: 'Mod-s', run: () => emitShortcut('save') },
            { key: 'Shift-Mod-s', run: () => emitShortcut('save-text') },
            { key: 'Mod-Enter', run: () => emitShortcut('validate') },
            { key: 'Alt-Shift-f', run: () => emitShortcut('format') },
          ]),
        ),
      ],
    }),
  })

  // Safari не даёт клавиатурно прокручивать overflow-region без собственного tab stop.
  view.scrollDOM.tabIndex = 0
  view.scrollDOM.setAttribute('aria-label', 'Прокручиваемая область редактора')

  void updateLanguage(props.formatId)
})

watch(
  () => props.modelValue,
  (nextValue) => {
    if (!view || nextValue === view.state.doc.toString()) {
      return
    }

    applyingExternalValue = true
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: nextValue } })
    applyingExternalValue = false
  },
)

watch(
  () => props.formatId,
  (formatId) => void updateLanguage(formatId),
)

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})

function applyCommand(commandId: string): boolean {
  return view ? applyEditorCommand(view, commandId) : false
}

function runUndo(): boolean {
  return view ? undo(view) : false
}

function runRedo(): boolean {
  return view ? redo(view) : false
}

function focus(): void {
  view?.focus()
}

function emitShortcut(action: 'save' | 'save-text' | 'validate' | 'format'): boolean {
  emit('shortcut', action)
  return true
}

async function updateLanguage(formatId: string): Promise<void> {
  const currentRevision = ++languageRevision
  const extension = await loadLanguageExtension(formatId)
  if (!view || currentRevision !== languageRevision) {
    return
  }

  view.dispatch({ effects: language.reconfigure(extension) })
}

async function loadLanguageExtension(formatId: string): Promise<Extension> {
  switch (formatId) {
    case 'markdown':
      return (await import('@codemirror/lang-markdown')).markdown()
    case 'html':
      return (await import('@codemirror/lang-html')).html()
    case 'css':
      return (await import('@codemirror/lang-css')).css()
    case 'javascript':
      return (await import('@codemirror/lang-javascript')).javascript()
    case 'json':
      return (await import('@codemirror/lang-json')).json()
    case 'yaml':
      return (await import('@codemirror/lang-yaml')).yaml()
    default:
      return []
  }
}

const editorTheme = EditorView.theme({
  '&': {
    minHeight: '32rem',
    color: 'var(--text-strong)',
    backgroundColor: 'transparent',
  },
  '.cm-scroller': {
    fontFamily: 'var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)',
    lineHeight: '1.65',
  },
  '.cm-content': {
    padding: '18px 0 28px',
    caretColor: 'var(--accent-deep)',
  },
  '.cm-gutters': {
    color: 'var(--text-muted)',
    backgroundColor: 'rgba(255, 250, 242, 0.42)',
    borderRight: '1px solid rgba(29, 92, 85, 0.12)',
  },
  '.cm-activeLine, .cm-activeLineGutter': {
    backgroundColor: 'rgba(255, 255, 255, 0.42)',
  },
  '&.cm-focused': {
    outline: '3px solid color-mix(in srgb, var(--accent) 42%, transparent)',
    outlineOffset: '-3px',
  },
})

defineExpose({ applyCommand, runUndo, runRedo, focus })
</script>

<template>
  <div ref="host" class="editor-codemirror"></div>
</template>

<style scoped>
.editor-codemirror {
  min-width: 0;
  overflow: clip;
  border-radius: inherit;
}
</style>
