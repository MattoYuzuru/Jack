<script setup lang="ts">
import { RouterLink } from 'vue-router'
import HomeToolIllustration from '../components/HomeToolIllustration.vue'

type ToolId = 'viewer' | 'converter' | 'compressor' | 'pdf' | 'editor' | 'devtools'

interface FoundationPillar {
  name: string
  detail: string
}

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

const signalPills = [
  'Iteration 03',
  'Viewer workspace',
  'Converter route',
  'Shared imaging layer',
  'OOXML document adapters',
  'Decode + encode + vector trace',
  'AVIF / ICO targets',
  'Preset profiles',
  'PSD / AI / EPS intake',
]

const foundationPillars: FoundationPillar[] = [
  {
    name: 'Палитра',
    detail: 'Тёплая sand-база, глубокий teal и янтарный акцент из логотипа Jack.',
  },
  {
    name: 'Типографика',
    detail: 'Контраст между display-заголовками и плотным интерфейсным текстом.',
  },
  {
    name: 'Архитектура',
    detail:
      'Home, viewer и converter разведены по маршрутам, а тяжёлые image-path, PDF/vector/icon targets и preset transform-профили живут в shared imaging + registry/strategy-слоях.',
  },
]

const toolCards: ToolCard[] = [
  {
    id: 'viewer',
    label: '01 · File Viewer',
    title: 'Viewer',
    description:
      'Рабочий маршрут для preview и анализа: image tooling, document search layer и OOXML-aware adapters внутри одного workspace.',
    detail:
      'Viewer уже закрывает весь стартовый image-format set, а теперь ещё поднимает PDF/TXT/CSV/HTML/RTF плюс DOCX/XLSX/PPTX previews и document search.',
    status: 'Active route',
    route: '/viewer',
    accents: ['Images', 'Docs', 'OOXML', 'Search'],
    span: 'tool-card--wide',
  },
  {
    id: 'converter',
    label: '02 · Conversion',
    title: 'Converter',
    description:
      'Browser-first converter уже поднимает image-, icon-, vector-, archive- и document-target сценарии через scenario registry, unified raster contract, preset transforms и decode/encode pipeline.',
    detail:
      'Теперь закрыты HEIC, TIFF, RAW, PSD, AI/EPS-preview paths, AVIF/ICO/vector trace targets и single-page PDF output поверх preset-driven profiles.',
    status: 'Active route',
    route: '/converter',
    accents: ['Image', 'AVIF', 'ICO', 'Presets'],
    span: 'tool-card--standard',
  },
  {
    id: 'compressor',
    label: '03 · Compression',
    title: 'Compressor',
    description:
      'Сжатие до практического минимума или под целевой размер с понятной визуализацией давления на файл.',
    detail: 'Логика подготовлена под quality presets и size targeting.',
    status: 'Queued',
    accents: ['Target size', 'Quality', 'Batch'],
    span: 'tool-card--standard',
  },
  {
    id: 'pdf',
    label: '04 · PDF Toolkit',
    title: 'PDF Toolkit',
    description:
      'Merge, split, rotate, OCR и сценарии защиты собираются в отдельный модуль с собственной рабочей зоной.',
    detail: 'Будущий центр для операций над страницами и безопасной правки PDF.',
    status: 'Queued',
    accents: ['Merge', 'OCR', 'Protect'],
    span: 'tool-card--standard',
  },
  {
    id: 'editor',
    label: '05 · Multi-Format Editor',
    title: 'Editor',
    description:
      'Редактор для текстовых форматов, Markdown, HTML, CSS и JS с live preview и рабочими шорткатами.',
    detail: 'Каркас под split-view и дальнейшие форматно-специфичные инструменты.',
    status: 'Queued',
    accents: ['Preview', 'Markdown', 'Format'],
    span: 'tool-card--standard',
  },
  {
    id: 'devtools',
    label: '06 · Dev Tools',
    title: 'Dev Utils',
    description:
      'Набор точечных утилит: декодеры, хеши, JWT, валидаторы и прочие ежедневные сценарии для разработчика.',
    detail: 'Будущий быстрый доступ к мелким, но частым инженерным операциям.',
    status: 'Queued',
    accents: ['JWT', 'Hashes', 'Validators'],
    span: 'tool-card--standard',
  },
]
</script>

<template>
  <main class="workspace-shell">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack of all trades</p>
          <p class="brand-lockup__title">Main Workspace</p>
        </div>
      </div>

      <div class="app-topbar__status">
        <span class="chip-pill">Iteration 03</span>
        <span class="chip-pill chip-pill--accent">Viewer + Converter</span>
      </div>
    </header>

    <section class="hero-grid">
      <article class="panel-surface hero-copy">
        <p class="eyebrow">Iteration 03 · Viewer + Converter</p>
        <h1>
          Главный экран Jack теперь ведёт сразу в viewer и converter с image-, document- и target-oriented runtime.
        </h1>
        <p class="lead">
          Home сохраняет soft industrial foundation, но теперь работает как switchboard для двух
          живых маршрутов: viewer отвечает за preview, analysis, PDF/text workflows и OOXML
          document adapters, а converter закрывает первую волну image-конвертаций, vector/icon
          targets, archive-friendly TIFF target и document-target через scenario registry, shared
          imaging и отдельные decode/encode стратегии.
        </p>

        <div class="signal-row">
          <span v-for="signal in signalPills" :key="signal" class="chip-pill">{{ signal }}</span>
        </div>

        <div class="stats-grid" aria-label="Ключевые показатели foundation">
          <article class="stats-card">
            <strong>3</strong>
            <span>экрана уже разведены через router</span>
          </article>
          <article class="stats-card">
            <strong>41</strong>
            <span
              >conversion-сценариев уже заведены в converter registry вместе с
              preset-профилями</span
            >
          </article>
          <article class="stats-card">
            <strong>7</strong>
            <span
              >слоёв foundation: shared imaging, registry, adapters, metadata, workspace state,
              color lab, document search</span
            >
          </article>
        </div>
      </article>

      <article class="panel-surface foundation-card">
        <div class="foundation-card__glow" aria-hidden="true"></div>
        <p class="eyebrow">System Notes</p>
        <h2>Foundation остаётся единым, а новые модули растут поверх него.</h2>

        <div class="foundation-pillars">
          <article v-for="pillar in foundationPillars" :key="pillar.name" class="foundation-pillar">
            <h3>{{ pillar.name }}</h3>
            <p>{{ pillar.detail }}</p>
          </article>
        </div>

        <div class="foundation-footer">
          <span class="chip-pill">Home grid</span>
          <span class="chip-pill">Viewer route</span>
          <span class="chip-pill">Shared tokens</span>
        </div>
      </article>
    </section>

    <section class="tool-grid" aria-label="Главные направления Jack">
      <article
        v-for="card in toolCards"
        :key="card.id"
        class="panel-surface tool-card"
        :class="[
          card.span,
          `tool-card--${card.id}`,
          { 'tool-card--interactive': Boolean(card.route) },
        ]"
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
          <div class="tool-card__actions">
            <p class="tool-card__detail">{{ card.detail }}</p>
            <RouterLink
              v-if="card.route"
              class="action-button action-button--accent"
              :to="card.route"
            >
              Open {{ card.title }}
            </RouterLink>
            <span v-else class="tool-card__queued">Roadmap queued</span>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.9fr);
  gap: 22px;
  margin-top: 22px;
}

.hero-copy,
.foundation-card {
  padding: 32px;
}

.hero-copy {
  animation: rise 0.75s ease-out both;
}

h1 {
  margin: 16px 0 0;
  max-width: 10ch;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(3rem, 4.8vw, 5.6rem);
  line-height: 0.94;
  letter-spacing: -0.04em;
}

.lead {
  margin: 22px 0 0;
  max-width: 62ch;
  color: var(--text-soft);
  font-size: 1.08rem;
}

.signal-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 26px;
}

.stats-card,
.foundation-pillar {
  padding: 18px;
  border-radius: var(--radius-xl);
  background: var(--surface-muted);
  box-shadow: var(--shadow-pressed);
}

.stats-card strong {
  display: block;
  color: var(--accent-cool-strong);
  font-family: var(--font-display);
  font-size: 2rem;
  line-height: 1;
}

.stats-card span {
  display: block;
  margin-top: 10px;
  color: var(--text-soft);
  font-size: 0.92rem;
}

.foundation-card {
  animation: rise 0.85s ease-out 0.08s both;
}

.foundation-card__glow {
  position: absolute;
  inset: -18% auto auto 58%;
  width: 220px;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(255, 195, 123, 0.92) 0%,
    rgba(255, 195, 123, 0.3) 36%,
    transparent 72%
  );
  filter: blur(10px);
  opacity: 0.88;
  pointer-events: none;
}

.foundation-card h2 {
  position: relative;
  z-index: 1;
  margin: 16px 0 0;
  max-width: 12ch;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(2rem, 3.4vw, 3rem);
  line-height: 0.98;
  letter-spacing: -0.03em;
}

.foundation-pillars {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 14px;
  margin-top: 24px;
}

.foundation-pillar h3 {
  margin: 0;
  color: var(--text-main);
  font-size: 0.95rem;
}

.foundation-pillar p {
  margin: 8px 0 0;
  color: var(--text-soft);
  font-size: 0.92rem;
}

.foundation-footer {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.tool-card {
  display: grid;
  gap: 20px;
  min-height: 380px;
  padding: 24px;
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

.tool-card--wide {
  grid-column: span 6;
}

.tool-card--standard {
  grid-column: span 3;
}

.tool-card__meta,
.tool-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.tool-card__meta {
  position: relative;
  z-index: 1;
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
  min-height: 160px;
  padding: 12px;
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
  font-size: clamp(2rem, 3vw, 3rem);
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
  margin-top: 10px;
}

.tool-card__footer {
  margin-top: auto;
}

.tool-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tool-card__actions {
  display: grid;
  justify-items: end;
  gap: 14px;
}

.tool-card__detail {
  max-width: 28ch;
  text-align: right;
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
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .tool-card--wide,
  .tool-card--standard {
    grid-column: span 6;
  }
}

@media (max-width: 860px) {
  h1,
  .foundation-card h2 {
    max-width: none;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  /* На планшетах и телефонах плитки идут в один поток, чтобы контент не спорил по высоте. */
  .tool-grid {
    grid-template-columns: 1fr;
  }

  .tool-card--wide,
  .tool-card--standard {
    grid-column: span 1;
  }
}

@media (max-width: 640px) {
  .hero-copy,
  .foundation-card,
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

  .tool-card__actions {
    justify-items: start;
  }

  .tool-card__detail {
    max-width: none;
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-copy,
  .foundation-card,
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
