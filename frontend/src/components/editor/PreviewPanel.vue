<script setup lang="ts">
import type { EditorLocalPreview } from '../../features/editor/application/editor-preview'

defineProps<{
  preview: EditorLocalPreview
  content: string
}>()
</script>

<template>
  <div class="editor-panel editor-panel--preview">
    <p class="eyebrow">Просмотр</p>
    <iframe
      v-if="preview.mode === 'sandbox'"
      class="editor-preview-frame"
      :srcdoc="preview.html"
      title="Предпросмотр документа"
      sandbox=""
    ></iframe>
    <div
      v-else-if="preview.mode === 'rich-html'"
      class="editor-rendered-preview"
      v-html="preview.html"
    ></div>
    <pre
      v-else-if="preview.mode === 'text'"
      class="editor-syntax-preview"
    ><code>{{ content }}</code></pre>
    <pre v-else class="editor-syntax-preview" v-html="preview.html"></pre>
  </div>
</template>

<style scoped>
.editor-panel {
  display: grid;
  gap: 16px;
  min-height: 620px;
}

.editor-preview-frame,
.editor-rendered-preview,
.editor-syntax-preview {
  width: 100%;
  min-height: 560px;
  border: 0;
  border-radius: 26px;
  box-shadow: var(--shadow-pressed);
}

.editor-preview-frame,
.editor-rendered-preview {
  background: rgba(255, 255, 255, 0.78);
}

.editor-rendered-preview,
.editor-syntax-preview {
  overflow: auto;
  margin: 0;
  padding: 20px;
}

.editor-syntax-preview {
  background: linear-gradient(180deg, rgba(16, 36, 38, 0.92), rgba(20, 48, 45, 0.96));
  color: rgba(255, 245, 232, 0.92);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.editor-rendered-preview :deep(pre) {
  overflow: auto;
  padding: 14px;
  border-radius: 18px;
  background: var(--accent-cool-strong);
  color: rgba(255, 250, 242, 0.92);
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

@media (max-width: 720px) {
  .editor-panel,
  .editor-preview-frame,
  .editor-rendered-preview,
  .editor-syntax-preview {
    min-height: 460px;
  }
}
</style>
