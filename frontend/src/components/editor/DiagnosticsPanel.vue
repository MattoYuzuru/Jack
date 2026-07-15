<script setup lang="ts">
import type { EditorIssue } from '../../features/editor/application/editor-server-runtime'

defineProps<{
  issues: EditorIssue[]
  fresh: boolean
  scopeLabel: string
}>()
</script>

<template>
  <div class="editor-panel">
    <p class="eyebrow">Проверка</p>
    <p class="editor-panel__note">
      {{
        fresh
          ? 'Показаны результаты для текущего текста.'
          : 'После изменений запусти проверку ещё раз.'
      }}
    </p>
    <p class="editor-panel__scope">{{ scopeLabel }}</p>
    <div v-if="issues.length" class="editor-issue-list">
      <article
        v-for="issue in issues"
        :key="`${issue.code}-${issue.line}-${issue.column}-${issue.message}`"
        class="editor-issue"
        :class="`editor-issue--${issue.severity}`"
      >
        <div class="editor-issue__topline">
          <span class="chip-pill chip-pill--compact">{{ issue.severity }}</span>
          <strong>{{ issue.code }}</strong>
          <span v-if="issue.line" class="editor-issue__position">
            {{ issue.line }}:{{ issue.column ?? 1
            }}<template v-if="issue.endLine"
              >–{{ issue.endLine }}:{{ issue.endColumn ?? issue.column ?? 1 }}</template
            >
          </span>
        </div>
        <p>{{ issue.message }}</p>
        <small v-if="issue.hint">{{ issue.hint }}</small>
        <code v-if="issue.quickFixCode" class="editor-issue__fix">{{ issue.quickFixCode }}</code>
      </article>
    </div>
    <p v-else class="editor-panel__note">Проверка ещё не запускалась или проблем не найдено.</p>
  </div>
</template>

<style scoped>
.editor-panel,
.editor-issue-list {
  display: grid;
  gap: 14px;
}

.editor-panel {
  min-height: 620px;
  align-content: start;
}
.editor-panel__note,
.editor-panel__scope,
.editor-issue p,
.editor-issue small {
  margin: 0;
  color: var(--text-soft);
}
.editor-panel__scope {
  margin: 0;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(29, 92, 85, 0.08);
  color: var(--accent-cool-strong);
  font-size: 0.82rem;
}
.editor-issue {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 250, 243, 0.82);
  box-shadow: var(--shadow-pressed);
}
.editor-issue--error {
  border: 1px solid rgba(243, 138, 85, 0.3);
}
.editor-issue--warning {
  border: 1px solid rgba(255, 207, 143, 0.36);
}
.editor-issue__topline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.editor-issue__position {
  color: var(--text-soft);
  font-size: 0.82rem;
}
.editor-issue__fix {
  width: fit-content;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(29, 92, 85, 0.1);
  color: var(--accent-cool-strong);
}
</style>
