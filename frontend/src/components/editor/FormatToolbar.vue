<script setup lang="ts">
import { ref } from 'vue'
import type { EditorFormatDefinition } from '../../features/editor/domain/editor-registry'
import TemplatePicker from './TemplatePicker.vue'

interface EditorTemplateOption {
  id: string
  label: string
  content: string
}

interface EditorHelperAction {
  id: string
  label: string
  detail: string
  shortcut?: string
}

defineProps<{
  formats: EditorFormatDefinition[]
  formatId: string
  fileName: string
  encoding: 'utf-8' | 'utf-8-bom'
  newline: 'lf' | 'crlf'
  persistenceEnabled: boolean
  templates: EditorTemplateOption[]
  selectedTemplateId: string
  currentContent: string
  helperActions: EditorHelperAction[]
  canFormat: boolean
  busy: boolean
  activeJob: boolean
  cancelling: boolean
  accept: string
}>()

const emit = defineEmits<{
  'update:formatId': [value: string]
  'update:fileName': [value: string]
  'update:encoding': [value: 'utf-8' | 'utf-8-bom']
  'update:newline': [value: 'lf' | 'crlf']
  'update:persistenceEnabled': [value: boolean]
  template: [templateId: string]
  command: [commandId: string]
  action: [
    action:
      | 'open'
      | 'undo'
      | 'redo'
      | 'format'
      | 'validate'
      | 'ready'
      | 'text'
      | 'cancel'
      | 'clear',
  ]
  file: [file: File]
}>()

const fileInput = ref<HTMLInputElement | null>(null)

function selectValue(event: Event): string {
  return (event.target as HTMLSelectElement).value
}

function inputValue(event: Event): string {
  return (event.target as HTMLInputElement).value
}

function onFileSelected(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    emit('file', file)
  }
  input.value = ''
}

function openFilePicker(): void {
  fileInput.value?.click()
  emit('action', 'open')
}
</script>

<template>
  <section class="format-toolbar" aria-label="Параметры и команды редактора">
    <div class="format-toolbar__fields">
      <label class="format-toolbar__field">
        <span>Формат</span>
        <select :value="formatId" @change="emit('update:formatId', selectValue($event))">
          <option v-for="format in formats" :key="format.id" :value="format.id">
            {{ format.label }}
          </option>
        </select>
      </label>

      <label class="format-toolbar__field format-toolbar__field--name">
        <span>Имя файла</span>
        <input
          :value="fileName"
          type="text"
          spellcheck="false"
          @input="emit('update:fileName', inputValue($event))"
        />
      </label>

      <label class="format-toolbar__field format-toolbar__field--compact">
        <span>Encoding</span>
        <select
          :value="encoding"
          @change="emit('update:encoding', selectValue($event) as 'utf-8' | 'utf-8-bom')"
        >
          <option value="utf-8">UTF-8</option>
          <option value="utf-8-bom">UTF-8 BOM</option>
        </select>
      </label>

      <label class="format-toolbar__field format-toolbar__field--compact">
        <span>Строки</span>
        <select
          :value="newline"
          @change="emit('update:newline', selectValue($event) as 'lf' | 'crlf')"
        >
          <option value="lf">LF</option>
          <option value="crlf">CRLF</option>
        </select>
      </label>

      <label class="format-toolbar__persistence">
        <input
          :checked="persistenceEnabled"
          type="checkbox"
          @change="emit('update:persistenceEnabled', ($event.target as HTMLInputElement).checked)"
        />
        <span>Recovery snapshot</span>
      </label>

      <TemplatePicker
        :model-value="selectedTemplateId"
        :templates="templates"
        :current-content="currentContent"
        @select="emit('template', $event)"
      />
    </div>

    <div class="format-toolbar__actions">
      <button class="action-button" type="button" @click="openFilePicker">Открыть</button>
      <button class="action-button" type="button" @click="emit('action', 'undo')">Отменить</button>
      <button class="action-button" type="button" @click="emit('action', 'redo')">Повторить</button>
      <button
        class="action-button"
        type="button"
        :disabled="!canFormat"
        @click="emit('action', 'format')"
      >
        Формат
      </button>
      <button
        class="action-button action-button--accent"
        type="button"
        :disabled="busy"
        @click="emit('action', 'validate')"
      >
        Проверить
      </button>
      <button class="action-button" type="button" :disabled="busy" @click="emit('action', 'ready')">
        Файл
      </button>
      <button class="action-button" type="button" :disabled="busy" @click="emit('action', 'text')">
        Текст
      </button>
      <button
        v-if="activeJob"
        class="action-button"
        type="button"
        :disabled="cancelling"
        @click="emit('action', 'cancel')"
      >
        Стоп
      </button>
      <button class="action-button" type="button" @click="emit('action', 'clear')">Очистить</button>
      <input
        ref="fileInput"
        class="format-toolbar__file"
        type="file"
        :accept="accept"
        @change="onFileSelected"
      />
    </div>

    <div class="format-toolbar__commands" aria-label="Быстрые действия по формату">
      <button
        v-for="command in helperActions"
        :key="command.id"
        class="icon-button format-toolbar__command"
        type="button"
        :title="command.detail"
        @click="emit('command', command.id)"
      >
        <span>{{ command.label }}</span
        ><small v-if="command.shortcut">{{ command.shortcut }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.format-toolbar,
.format-toolbar__fields,
.format-toolbar__actions,
.format-toolbar__commands {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.format-toolbar {
  justify-content: space-between;
  margin-bottom: 16px;
}

.format-toolbar__fields {
  align-items: flex-end;
}

.format-toolbar__field {
  display: grid;
  gap: 7px;
  min-width: 150px;
}

.format-toolbar__field--name {
  min-width: min(220px, 100%);
}
.format-toolbar__field--compact {
  min-width: 116px;
}
.format-toolbar__field span,
.format-toolbar__persistence span {
  color: var(--text-soft);
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.format-toolbar__field select,
.format-toolbar__field input {
  width: 100%;
  min-height: 48px;
  padding: 11px 13px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 18px;
  background: rgba(255, 250, 243, 0.78);
  box-shadow: var(--shadow-pressed);
  color: var(--text-strong);
}
.format-toolbar__field select:focus-visible,
.format-toolbar__field input:focus-visible,
.format-toolbar__persistence input:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.42);
  outline-offset: 3px;
}
.format-toolbar__persistence {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
}
.format-toolbar__persistence input {
  width: 24px;
  height: 24px;
  accent-color: var(--accent-cool);
}
.format-toolbar__actions {
  justify-content: flex-end;
  align-items: center;
}
.format-toolbar__actions .action-button {
  min-width: 0;
  padding-inline: 14px;
}
.format-toolbar__commands {
  flex-basis: 100%;
  margin-top: 2px;
}
.format-toolbar__command {
  display: grid;
  gap: 2px;
  min-width: 82px;
  padding-block: 10px;
}
.format-toolbar__command small {
  color: var(--accent-coral);
  font-size: 0.68rem;
}
.format-toolbar__file {
  display: none;
}

@media (max-width: 720px) {
  .format-toolbar__field,
  .format-toolbar__field--name {
    min-width: min(100%, 180px);
    flex: 1 1 140px;
  }
  .format-toolbar__actions {
    justify-content: flex-start;
  }
}
</style>
