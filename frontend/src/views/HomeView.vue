<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import HomeToolIllustration from '../components/HomeToolIllustration.vue'
import { getProcessingPlatformModules } from '../features/processing/domain/platform-registry'
import type { ProcessingPlatformModuleCapability } from '../features/processing/application/processing-client'

type ToolId = 'viewer' | 'converter' | 'compressor' | 'pdf' | 'editor' | 'devtools'

interface ToolCard {
  id: ToolId
  label: string
  title: string
  description: string
  detail: string
  status: string
  route?: string
  accents: string[]
  span: 'tool-card--wide' | 'tool-card--standard'
}

const signalPills = ['Просмотр', 'Конвертация', 'Сжатие', 'PDF', 'Редактор', 'Dev Utils']

const toolCardBlueprints: ToolCard[] = [
  {
    id: 'viewer',
    label: '01 · File Viewer',
    title: 'Viewer',
    description: 'Документы, таблицы, изображения, видео и аудио в одном окне.',
    detail: 'Открыть и просмотреть',
    status: 'Доступно',
    route: '/viewer',
    accents: ['Документы', 'Изображения', 'Медиа', 'Поиск'],
    span: 'tool-card--wide',
  },
  {
    id: 'converter',
    label: '02 · Conversion',
    title: 'Converter',
    description: 'Смена формата для изображений, документов и медиа.',
    detail: 'Выбрать формат и скачать',
    status: 'Доступно',
    route: '/converter',
    accents: ['Изображения', 'Документы', 'Медиа', 'Профили'],
    span: 'tool-card--standard',
  },
  {
    id: 'compressor',
    label: '03 · Compression',
    title: 'Compressor',
    description: 'Сожми файл под лимит или до лёгкого рабочего размера.',
    detail: 'Лимит, качество, история',
    status: 'Доступно',
    route: '/compression',
    accents: ['Лимит размера', 'Качество', 'История'],
    span: 'tool-card--standard',
  },
  {
    id: 'pdf',
    label: '04 · PDF Toolkit',
    title: 'PDF Toolkit',
    description: 'Объединение, OCR, подпись, защита и правки PDF.',
    detail: 'Открыть документ и запустить операцию',
    status: 'Доступно',
    route: '/pdf-toolkit',
    accents: ['Объединение', 'OCR', 'Защита'],
    span: 'tool-card--standard',
  },
  {
    id: 'editor',
    label: '05 · Multi-Format Editor',
    title: 'Editor',
    description: 'Markdown, HTML, JSON, YAML и обычный текст в одном редакторе.',
    detail: 'Правка, preview, экспорт',
    status: 'Доступно',
    route: '/editor',
    accents: ['Просмотр', 'Markdown', 'Форматирование'],
    span: 'tool-card--standard',
  },
  {
    id: 'devtools',
    label: '06 · Dev Tools',
    title: 'Dev Utils',
    description: 'Кодировки, JWT, ссылки, хэши, валидаторы и быстрые утилиты.',
    detail: 'Вставить данные и скопировать результат',
    status: 'Доступно',
    route: '/dev-tools',
    accents: ['JWT', 'Хэши', 'Проверка', 'Ссылки'],
    span: 'tool-card--standard',
  },
]

const toolCards = ref<ToolCard[]>(toolCardBlueprints)
const platformModuleIdByToolId: Partial<Record<ToolId, string>> = {
  compressor: 'compression',
  editor: 'multi-format-editor',
}

function applyPlatformModule(
  card: ToolCard,
  module: ProcessingPlatformModuleCapability | undefined,
): ToolCard {
  if (!module || card.route) {
    return card
  }

  return {
    ...card,
    detail: module.summary,
    status: module.foundationReady ? 'Скоро · Основа готова' : 'Скоро · Нужна доработка',
    accents: module.accents.slice(0, 3).length ? module.accents.slice(0, 3) : card.accents,
  }
}

async function hydrateQueuedModuleCards(): Promise<void> {
  const modules = await getProcessingPlatformModules()
  const moduleById = new Map(modules.map((module) => [module.id, module]))

  toolCards.value = toolCardBlueprints.map((card) =>
    applyPlatformModule(card, moduleById.get(platformModuleIdByToolId[card.id] ?? '')),
  )
}

void hydrateQueuedModuleCards().catch(() => undefined)
</script>

<template>
  <main class="workspace-shell">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack · Workspace</p>
          <p class="brand-lockup__title">Главное рабочее пространство</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <span class="chip-pill">6 инструментов</span>
        <span class="chip-pill chip-pill--accent">Файлы, PDF, текст и dev-задачи</span>
      </div>
    </header>

    <section class="hero-grid">
      <article class="panel-surface hero-copy">
        <p class="eyebrow">Jack Workspace</p>
        <h1>Выбери задачу и сразу открой нужный инструмент.</h1>
        <p class="lead">
          Файлы, PDF, текст и ежедневные dev-задачи собраны в одном наборе экранов. Нажми на нужный
          блок и переходи сразу к работе.
        </p>

        <div class="signal-row">
          <span v-for="signal in signalPills" :key="signal" class="chip-pill">{{ signal }}</span>
        </div>
      </article>
    </section>

    <section class="tool-grid" aria-label="Главные направления Jack">
      <component
        v-for="card in toolCards"
        :key="card.id"
        :is="card.route ? RouterLink : 'article'"
        v-bind="card.route ? { to: card.route } : {}"
        class="panel-surface tool-card"
        :class="[
          card.span,
          `tool-card--${card.id}`,
          { 'tool-card--interactive': Boolean(card.route) },
        ]"
        :aria-label="card.route ? `Открыть ${card.title}` : undefined"
      >
        <div class="tool-card__meta">
          <p class="tool-card__eyebrow">{{ card.label }}</p>
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{ card.status }}</span>
        </div>

        <div class="tool-card__art" aria-hidden="true">
          <HomeToolIllustration :id="card.id" />
        </div>

        <div class="tool-card__copy">
          <h2>{{ card.title }}</h2>
          <p class="tool-card__description">{{ card.description }}</p>
        </div>

        <div class="tool-card__footer">
          <div class="tool-card__tags">
            <span v-for="accent in card.accents" :key="accent" class="chip-pill chip-pill--compact">
              {{ accent }}
            </span>
          </div>
          <p v-if="card.route" class="tool-card__detail">{{ card.detail }}</p>
          <span v-else class="tool-card__queued">Скоро здесь</span>
        </div>
      </component>
    </section>
  </main>
</template>

<style scoped>
.hero-grid {
  display: grid;
  margin-top: 22px;
}

.hero-copy {
  padding: 32px;
}

.hero-copy {
  animation: rise 0.75s ease-out both;
}

h1 {
  margin: 16px 0 0;
  max-width: 11ch;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 4.4vw, 4.8rem);
  line-height: 0.96;
  letter-spacing: -0.04em;
}

.lead {
  margin: 18px 0 0;
  max-width: 56ch;
  color: var(--text-soft);
  font-size: 1rem;
}

.signal-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.tool-card {
  display: grid;
  gap: 18px;
  min-height: 320px;
  padding: 24px;
  color: inherit;
  text-decoration: none;
  transition:
    transform 240ms ease,
    box-shadow 240ms ease;
  animation: rise 0.82s ease-out both;
}

.tool-card:nth-child(2) {
  animation-delay: 0.05s;
}

.tool-card:nth-child(3) {
  animation-delay: 0.1s;
}

.tool-card:nth-child(4) {
  animation-delay: 0.15s;
}

.tool-card:nth-child(5) {
  animation-delay: 0.2s;
}

.tool-card:nth-child(6) {
  animation-delay: 0.25s;
}

.tool-card::after {
  content: '';
  position: absolute;
  inset: auto -10% -35% auto;
  width: 200px;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.55), transparent 70%);
  opacity: 0.9;
  pointer-events: none;
}

.tool-card--interactive:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-floating);
}

.tool-card--interactive {
  cursor: pointer;
}

.tool-card--interactive:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.34);
  outline-offset: 4px;
}

.tool-card--wide {
  grid-column: auto;
}

.tool-card--standard {
  grid-column: auto;
}

.tool-card__meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.tool-card__eyebrow {
  margin: 0;
  color: var(--accent-coral);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.tool-card__art {
  display: grid;
  place-items: center;
  min-height: 140px;
  padding: 10px;
  border-radius: calc(var(--radius-2xl) - 10px);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.58), rgba(228, 219, 205, 0.5));
  box-shadow: var(--shadow-pressed);
}

.tool-card__copy,
.tool-card__footer {
  position: relative;
  z-index: 1;
}

.tool-card__copy h2 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 2.8vw, 2.6rem);
  letter-spacing: -0.04em;
}

.tool-card__description,
.tool-card__detail,
.tool-card__queued {
  margin: 0;
  color: var(--text-soft);
  font-size: 0.98rem;
}

.tool-card__description {
  margin-top: 8px;
}

.tool-card__footer {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 12px;
  margin-top: auto;
}

.tool-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tool-card__detail {
  color: var(--accent-cool-strong);
  font-weight: 700;
}

.tool-card__queued {
  font-weight: 700;
}

.tool-card--viewer {
  background:
    radial-gradient(circle at top right, rgba(255, 191, 118, 0.36), transparent 28%),
    var(--surface-panel);
}

.tool-card--converter {
  background:
    radial-gradient(circle at top left, rgba(243, 138, 85, 0.22), transparent 26%),
    var(--surface-panel);
}

.tool-card--compressor {
  background:
    radial-gradient(circle at top center, rgba(29, 92, 85, 0.18), transparent 24%),
    var(--surface-panel);
}

.tool-card--pdf {
  background:
    radial-gradient(circle at top right, rgba(248, 215, 172, 0.42), transparent 26%),
    var(--surface-panel);
}

.tool-card--editor {
  background:
    radial-gradient(circle at top left, rgba(255, 176, 111, 0.24), transparent 28%),
    var(--surface-panel);
}

.tool-card--devtools {
  background:
    radial-gradient(circle at top right, rgba(29, 92, 85, 0.18), transparent 25%),
    var(--surface-panel);
}

@media (max-width: 1180px) {
  .tool-card--wide,
  .tool-card--standard {
    grid-column: auto;
  }
}

@media (max-width: 860px) {
  h1 {
    max-width: none;
  }

  .tool-card--wide,
  .tool-card--standard {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .hero-copy,
  .tool-card {
    padding: 20px;
  }

  .tool-card {
    min-height: auto;
  }

  .tool-card__meta,
  .tool-card__footer {
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-copy,
  .tool-card {
    animation: none;
  }

  .tool-card {
    transition: none;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translate3d(0, 24px, 0);
  }

  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}
</style>
