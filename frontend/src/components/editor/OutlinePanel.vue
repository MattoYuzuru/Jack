<script setup lang="ts">
import type { EditorOutlineItem } from '../../features/editor/application/editor-server-runtime'

defineProps<{
  items: EditorOutlineItem[]
  suggestions: string[]
}>()
</script>

<template>
  <div class="editor-panel">
    <p class="eyebrow">Структура</p>
    <p class="editor-panel__note">Локальная структура заменяется более точной после проверки.</p>
    <div v-if="items.length" class="editor-outline">
      <div
        v-for="item in items"
        :key="item.id"
        class="editor-outline__item"
        :style="{ paddingInlineStart: `${12 + (item.depth - 1) * 18}px` }"
      >
        <strong>{{ item.label }}</strong
        ><span>{{ item.kind }}</span>
      </div>
    </div>
    <p v-else class="editor-panel__note">Для текущего текста структура пока не выделилась.</p>
    <div class="editor-suggestions">
      <span
        v-for="suggestion in suggestions"
        :key="suggestion"
        class="chip-pill chip-pill--compact"
        >{{ suggestion }}</span
      >
    </div>
  </div>
</template>

<style scoped>
.editor-panel,
.editor-outline {
  display: grid;
  gap: 12px;
}
.editor-panel {
  min-height: 620px;
  align-content: start;
}
.editor-panel__note {
  margin: 0;
  color: var(--text-soft);
}
.editor-outline__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 250, 243, 0.82);
  box-shadow: var(--shadow-pressed);
}
.editor-outline__item span {
  color: var(--text-soft);
  font-size: 0.82rem;
}
.editor-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
