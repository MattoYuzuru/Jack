<script setup lang="ts">
import { computed, ref } from 'vue'

interface EditorTemplateOption {
  id: string
  label: string
  content: string
}

const props = defineProps<{
  modelValue: string
  templates: EditorTemplateOption[]
  currentContent: string
}>()

const emit = defineEmits<{
  select: [templateId: string]
}>()

const query = ref('')
const previewId = ref('')
const filteredTemplates = computed(() => {
  const normalizedQuery = query.value.trim().toLocaleLowerCase('ru')
  if (!normalizedQuery) {
    return props.templates
  }
  return props.templates.filter((template) =>
    `${template.label} ${template.content}`.toLocaleLowerCase('ru').includes(normalizedQuery),
  )
})
const previewTemplate = computed(
  () =>
    props.templates.find((template) => template.id === previewId.value) ??
    props.templates.find((template) => template.id === props.modelValue) ??
    filteredTemplates.value[0] ??
    null,
)
const diffStats = computed(() =>
  previewTemplate.value
    ? calculateLineDiff(props.currentContent, previewTemplate.value.content)
    : { added: 0, removed: 0 },
)

function calculateLineDiff(before: string, after: string): { added: number; removed: number } {
  const beforeLines = before.split('\n')
  const afterLines = after.split('\n')
  const rows = beforeLines.length + 1
  const columns = afterLines.length + 1
  const common = Array.from({ length: rows }, () => new Uint16Array(columns))

  // Небольшой LCS нужен только для preview встроенных шаблонов и не работает на пользовательских файлах.
  for (let row = 1; row < rows; row += 1) {
    for (let column = 1; column < columns; column += 1) {
      common[row]![column] =
        beforeLines[row - 1] === afterLines[column - 1]
          ? common[row - 1]![column - 1]! + 1
          : Math.max(common[row - 1]![column]!, common[row]![column - 1]!)
    }
  }

  const unchanged = common[rows - 1]![columns - 1]!
  return { added: afterLines.length - unchanged, removed: beforeLines.length - unchanged }
}
</script>

<template>
  <details class="template-picker">
    <summary class="action-button">Палитра шаблонов</summary>
    <div class="template-picker__popover">
      <label class="template-picker__search">
        <span>Поиск шаблона</span>
        <input v-model="query" type="search" placeholder="Название или фрагмент…" />
      </label>

      <div class="template-picker__layout">
        <div class="template-picker__list" role="listbox" aria-label="Шаблоны текущего формата">
          <button
            v-for="template in filteredTemplates"
            :key="template.id"
            class="template-picker__option"
            :class="{ 'template-picker__option--active': template.id === previewTemplate?.id }"
            type="button"
            role="option"
            :aria-selected="template.id === modelValue"
            @mouseenter="previewId = template.id"
            @focus="previewId = template.id"
            @click="emit('select', template.id)"
          >
            <strong>{{ template.label }}</strong>
            <small>{{ template.id === modelValue ? 'Выбран' : 'Посмотреть и применить' }}</small>
          </button>
          <p v-if="!filteredTemplates.length" class="template-picker__empty">Ничего не найдено.</p>
        </div>

        <section v-if="previewTemplate" class="template-picker__preview" aria-live="polite">
          <div class="template-picker__diff">
            <strong>{{ previewTemplate.label }}</strong>
            <span>+{{ diffStats.added }} строк · −{{ diffStats.removed }} строк</span>
          </div>
          <pre><code>{{ previewTemplate.content }}</code></pre>
        </section>
      </div>
    </div>
  </details>
</template>

<style scoped>
.template-picker {
  position: relative;
}

.template-picker summary {
  list-style: none;
}

.template-picker summary::-webkit-details-marker {
  display: none;
}

.template-picker__popover {
  position: absolute;
  z-index: 20;
  top: calc(100% + 10px);
  left: 0;
  width: min(680px, calc(100vw - 64px));
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 24px;
  background: rgba(237, 229, 217, 0.98);
  box-shadow: var(--shadow-floating);
}

.template-picker__search {
  display: grid;
  gap: 6px;
}

.template-picker__search span,
.template-picker__option small,
.template-picker__diff span,
.template-picker__empty {
  color: var(--text-soft);
  font-size: 0.78rem;
}

.template-picker__search input {
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid rgba(29, 92, 85, 0.14);
  border-radius: 16px;
  background: rgba(255, 250, 243, 0.88);
  color: var(--text-strong);
}

.template-picker__layout {
  display: grid;
  grid-template-columns: minmax(180px, 0.7fr) minmax(240px, 1.3fr);
  gap: 14px;
  margin-top: 14px;
}

.template-picker__list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.template-picker__option {
  display: grid;
  gap: 3px;
  min-height: 52px;
  padding: 10px 12px;
  border: 0;
  border-radius: 16px;
  background: transparent;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}

.template-picker__option:hover,
.template-picker__option:focus-visible,
.template-picker__option--active {
  outline: none;
  background: rgba(255, 250, 243, 0.8);
  box-shadow: var(--shadow-pressed);
}

.template-picker__preview {
  min-width: 0;
}

.template-picker__diff {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.template-picker__preview pre {
  overflow: auto;
  max-height: 280px;
  margin: 0;
  padding: 14px;
  border-radius: 18px;
  background: var(--accent-cool-strong);
  color: rgba(255, 250, 242, 0.92);
  font-size: 0.78rem;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .template-picker__popover {
    position: fixed;
    inset: auto 16px 16px;
    width: auto;
    max-height: calc(100dvh - 32px);
    overflow: auto;
  }

  .template-picker__layout {
    grid-template-columns: 1fr;
  }
}
</style>
