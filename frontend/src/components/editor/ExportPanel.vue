<script setup lang="ts">
interface EditorFact {
  label: string
  value: string
}

defineProps<{
  facts: EditorFact[]
  busy: boolean
}>()

const emit = defineEmits<{
  validate: []
  downloadReady: []
  downloadText: []
}>()
</script>

<template>
  <div class="editor-panel">
    <p class="eyebrow">Экспорт</p>
    <div class="editor-facts">
      <article v-for="fact in facts" :key="fact.label" class="editor-fact">
        <span>{{ fact.label }}</span
        ><strong>{{ fact.value }}</strong>
      </article>
    </div>
    <div class="editor-export-actions">
      <button
        class="action-button action-button--accent"
        type="button"
        :disabled="busy"
        @click="emit('validate')"
      >
        Обновить проверку
      </button>
      <button class="action-button" type="button" :disabled="busy" @click="emit('downloadReady')">
        Скачать файл
      </button>
      <button class="action-button" type="button" :disabled="busy" @click="emit('downloadText')">
        Скачать текст
      </button>
    </div>
  </div>
</template>

<style scoped>
.editor-panel,
.editor-facts {
  display: grid;
  gap: 12px;
}
.editor-panel {
  min-height: 620px;
}
.editor-fact {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 250, 243, 0.82);
  box-shadow: var(--shadow-pressed);
}
.editor-fact span {
  color: var(--text-soft);
  font-size: 0.82rem;
}
.editor-fact strong {
  color: var(--text-strong);
}
.editor-export-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: auto;
}
</style>
