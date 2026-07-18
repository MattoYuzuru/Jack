<script setup lang="ts">
import { computed } from 'vue'
import type { ViewerResolvedDocument } from '../../application/viewer-runtime'

const props = defineProps<{
  selection: ViewerResolvedDocument
  modeLabel: string
  metrics: string[]
  actionMessage: string
  canQuickEdit: boolean
  searchQuery: string
  slideIndex: number
}>()

const emit = defineEmits<{
  copyText: []
  downloadText: []
  openEditor: []
  clearSearch: []
  selectSlide: [index: number]
}>()

const activeSlide = computed(() =>
  props.selection.layout.mode === 'slides'
    ? (props.selection.layout.slides[props.slideIndex] ?? props.selection.layout.slides[0] ?? null)
    : null,
)
</script>

<template>
  <div class="viewer-document-frame" data-testid="viewer-document-renderer">
    <div class="document-stage-hud">
      <div class="document-stage-hud__meta">
        <span class="chip-pill chip-pill--compact chip-pill--accent">{{
          selection.format.label
        }}</span>
        <span class="chip-pill chip-pill--compact">{{ modeLabel }}</span>
        <span v-for="metric in metrics" :key="metric" class="chip-pill chip-pill--compact">{{
          metric
        }}</span>
      </div>
      <div class="document-stage-hud__actions">
        <button class="action-button" type="button" @click="emit('copyText')">
          Скопировать текст
        </button>
        <button class="action-button" type="button" @click="emit('downloadText')">
          Скачать текст
        </button>
        <button
          v-if="canQuickEdit"
          class="action-button action-button--accent"
          type="button"
          @click="emit('openEditor')"
        >
          Открыть в Editor
        </button>
        <button
          class="action-button"
          type="button"
          :disabled="!searchQuery"
          @click="emit('clearSearch')"
        >
          Сбросить поиск
        </button>
      </div>
    </div>
    <p v-if="actionMessage" class="document-action-message" role="status">
      {{ actionMessage }}
    </p>
    <iframe
      v-if="selection.layout.mode === 'pdf'"
      class="viewer-document-frame__embed"
      :src="selection.layout.objectUrl"
      :title="selection.file.name"
    ></iframe>
    <iframe
      v-else-if="selection.layout.mode === 'html'"
      class="viewer-document-frame__embed"
      sandbox=""
      :srcdoc="selection.layout.srcDoc"
      :title="selection.file.name"
    ></iframe>
    <div v-else-if="selection.layout.mode === 'slides'" class="document-slide-grid">
      <article v-if="activeSlide" class="document-slide-card document-slide-card--focus">
        <span class="chip-pill chip-pill--compact chip-pill--accent"
          >Активный слайд {{ slideIndex + 1 }}</span
        >
        <h3>{{ activeSlide.title }}</h3>
        <ul v-if="activeSlide.bullets.length">
          <li v-for="bullet in activeSlide.bullets" :key="bullet">
            {{ bullet }}
          </li>
        </ul>
        <p v-else>На выбранном слайде нет пунктов списка.</p>
      </article>
      <div class="document-slide-rail" role="tablist" aria-label="Слайды">
        <button
          v-for="(slide, index) in selection.layout.slides"
          :key="slide.id"
          class="document-slide-chip"
          :class="{ 'document-slide-chip--active': slideIndex === index }"
          type="button"
          role="tab"
          :aria-selected="slideIndex === index"
          @click="emit('selectSlide', index)"
        >
          {{ index + 1 }} · {{ slide.title }}
        </button>
      </div>
    </div>
    <article v-else-if="selection.layout.mode === 'text'" class="document-text">
      <p v-for="(paragraph, index) in selection.layout.paragraphs" :key="index">
        {{ paragraph }}
      </p>
      <p v-if="!selection.layout.paragraphs.length">Документ пуст.</p>
    </article>
  </div>
</template>

<style scoped>
.viewer-document-frame {
  display: grid;
  width: 100%;
  min-height: 480px;
  place-items: start center;
  overflow: auto;
}
.document-stage-hud,
.document-stage-hud__meta,
.document-stage-hud__actions,
.document-slide-rail {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.document-stage-hud {
  width: 100%;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.document-stage-hud__meta {
  align-items: center;
}
.document-stage-hud__actions {
  justify-content: flex-end;
}
.document-action-message {
  width: 100%;
  margin: 0 0 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 246, 232, 0.9);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}
.viewer-document-frame__embed {
  width: 100%;
  min-height: 70vh;
  border: 0;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: var(--shadow-pressed);
}
.document-text,
.document-slide-grid {
  display: grid;
  width: 100%;
  gap: 14px;
}
.document-text p,
.document-slide-card {
  margin: 0;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}
.document-slide-card {
  display: grid;
  gap: 14px;
}
.document-slide-card h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
}
.document-slide-rail {
  grid-column: 1 / -1;
}
.document-slide-chip {
  min-height: 44px;
  padding: 8px 12px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-soft);
  cursor: pointer;
}
.document-slide-chip--active {
  color: var(--accent-cool-strong);
}
</style>
