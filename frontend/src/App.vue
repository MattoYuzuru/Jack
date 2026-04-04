<script setup lang="ts">
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
  accents: string[]
  span: 'tool-card--wide' | 'tool-card--standard'
}

const signalPills = [
  'Iteration 01',
  'Neumorphism 2026',
  'Responsive home hub',
  'SVG-first navigation',
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
    name: 'Поведение',
    detail: 'Мягкая объёмность, понятные hover/focus-состояния и чистый mobile-flow.',
  },
]

// Карта направлений повторяет крупные блоки roadmap, чтобы home-экран сразу отражал scope продукта.
const toolCards: ToolCard[] = [
  {
    id: 'viewer',
    label: '01 · File Viewer',
    title: 'Viewer',
    description:
      'Точка входа в просмотр изображений, видео, аудио, PDF и офисных форматов с дальнейшим переходом в tool-specific режимы.',
    detail: 'Приоритетная зона предпросмотра и навигации по типам файлов.',
    status: 'Нерабочее',
    accents: ['Images', 'Docs', 'Media'],
    span: 'tool-card--wide',
  },
  {
    id: 'converter',
    label: '02 · Conversion',
    title: 'Converter',
    description:
      'Частые и тяжёлые конвертации собраны в отдельный быстрый маршрут без перегруза второстепенными настройками.',
    detail: 'Подготовлен как будущий switchboard для форматов и пресетов.',
    status: 'Нерабочее',
    accents: ['Image', 'Office', 'Video'],
    span: 'tool-card--standard',
  },
  {
    id: 'compressor',
    label: '03 · Compression',
    title: 'Compressor',
    description:
      'Сжатие до практического минимума или под целевой размер с понятной визуализацией давления на файл.',
    detail: 'Логика подготовлена под quality presets и size targeting.',
    status: 'Нерабочее',
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
    status: 'Нерабочее',
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
    status: 'Нерабочее',
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
    status: 'Нерабочее',
    accents: ['JWT', 'Hashes', 'Validators'],
    span: 'tool-card--standard',
  },
]
</script>

<template>
  <main class="workspace">
    <header class="panel topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Jack of all trades</p>
          <p class="brand-lockup__title">Main Workspace</p>
        </div>
      </div>

      <div class="topbar__status">
        <span class="topbar__chip">UI Foundation</span>
        <span class="topbar__chip topbar__chip--accent">Active Screen</span>
      </div>
    </header>

    <section class="hero-grid">
      <article class="panel hero-copy">
        <p class="eyebrow">Iteration 01 · UI/UX Foundation</p>
        <h1>Главный экран Jack как мягкий big-tech control center.</h1>
        <p class="lead">
          Новый foundation опирается на soft industrial neumorphism: тактильные поверхности,
          согласованную палитру из логотипа, крупные SVG-навигационные плитки и чистую адаптацию под
          широкие и мобильные экраны.
        </p>

        <div class="signal-row">
          <span v-for="signal in signalPills" :key="signal">{{ signal }}</span>
        </div>

        <div class="stats-grid" aria-label="Ключевые показатели foundation">
          <article class="stats-card">
            <strong>6</strong>
            <span>продуктовых модулей на home-сетке</span>
          </article>
          <article class="stats-card">
            <strong>3</strong>
            <span>слоя foundation: цвет, тип, поведение</span>
          </article>
          <article class="stats-card">
            <strong>100%</strong>
            <span>адаптивная композиция для desktop, tablet и mobile</span>
          </article>
        </div>
      </article>

      <article class="panel foundation-card">
        <div class="foundation-card__glow" aria-hidden="true"></div>
        <p class="eyebrow">System Notes</p>
        <h2>Foundation собран под дальнейшие продуктовые итерации.</h2>

        <div class="foundation-pillars">
          <article v-for="pillar in foundationPillars" :key="pillar.name" class="foundation-pillar">
            <h3>{{ pillar.name }}</h3>
            <p>{{ pillar.detail }}</p>
          </article>
        </div>

        <div class="foundation-footer">
          <span>Home grid</span>
          <span>Soft depth</span>
          <span>Clear contrast</span>
        </div>
      </article>
    </section>

    <section class="tool-grid" aria-label="Главные направления Jack">
      <article
        v-for="card in toolCards"
        :key="card.id"
        class="panel tool-card"
        :class="[card.span, `tool-card--${card.id}`]"
      >
        <div class="tool-card__meta">
          <p class="tool-card__eyebrow">{{ card.label }}</p>
          <span class="status-badge">{{ card.status }}</span>
        </div>

        <div class="tool-card__art" aria-hidden="true">
          <svg v-if="card.id === 'viewer'" viewBox="0 0 280 190" class="tool-icon">
            <defs>
              <linearGradient id="viewerOrb" x1="0%" x2="100%" y1="0%" y2="100%">
                <stop offset="0%" stop-color="#fff4e5" />
                <stop offset="100%" stop-color="#ffb06f" />
              </linearGradient>
            </defs>
            <ellipse
              cx="140"
              cy="96"
              rx="94"
              ry="52"
              fill="none"
              stroke="#1d5c55"
              stroke-width="14"
            />
            <circle cx="140" cy="96" r="28" fill="#1d5c55" />
            <circle cx="150" cy="88" r="12" fill="url(#viewerOrb)" />
            <path
              d="M56 96c17-28 48-49 84-49s67 21 84 49"
              fill="none"
              stroke="#f38a55"
              stroke-linecap="round"
              stroke-width="9"
            />
            <path
              d="M56 96c17 28 48 49 84 49s67-21 84-49"
              fill="none"
              stroke="#f8d7ac"
              stroke-linecap="round"
              stroke-width="9"
            />
            <path
              d="M82 52c6-8 18-14 30-16"
              fill="none"
              stroke="#ffffff"
              stroke-linecap="round"
              stroke-width="7"
            />
          </svg>

          <svg v-else-if="card.id === 'converter'" viewBox="0 0 280 190" class="tool-icon">
            <rect
              x="97"
              y="44"
              width="86"
              height="102"
              rx="24"
              fill="#efe5d7"
              stroke="#1d5c55"
              stroke-width="10"
            />
            <path d="M111 82h58" stroke="#f38a55" stroke-linecap="round" stroke-width="10" />
            <path d="M111 108h42" stroke="#f8d7ac" stroke-linecap="round" stroke-width="10" />
            <path
              d="M69 90c0-28 22-50 50-50"
              fill="none"
              stroke="#1d5c55"
              stroke-linecap="round"
              stroke-width="12"
            />
            <path
              d="M105 28l22 12-22 12"
              fill="none"
              stroke="#1d5c55"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="12"
            />
            <path
              d="M211 100c0 28-22 50-50 50"
              fill="none"
              stroke="#f38a55"
              stroke-linecap="round"
              stroke-width="12"
            />
            <path
              d="M175 162l-22-12 22-12"
              fill="none"
              stroke="#f38a55"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="12"
            />
          </svg>

          <svg v-else-if="card.id === 'compressor'" viewBox="0 0 280 190" class="tool-icon">
            <rect
              x="78"
              y="42"
              width="124"
              height="104"
              rx="28"
              fill="#efe5d7"
              stroke="#1d5c55"
              stroke-width="10"
            />
            <path d="M112 64v58" stroke="#f38a55" stroke-linecap="round" stroke-width="12" />
            <path d="M140 64v58" stroke="#1d5c55" stroke-linecap="round" stroke-width="12" />
            <path d="M168 64v58" stroke="#f8d7ac" stroke-linecap="round" stroke-width="12" />
            <path
              d="M97 32l18 18-18 18"
              fill="none"
              stroke="#ffffff"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="10"
            />
            <path
              d="M183 158l-18-18 18-18"
              fill="none"
              stroke="#ffffff"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="10"
            />
            <path d="M50 94h42" stroke="#1d5c55" stroke-linecap="round" stroke-width="12" />
            <path d="M188 94h42" stroke="#f38a55" stroke-linecap="round" stroke-width="12" />
          </svg>

          <svg v-else-if="card.id === 'pdf'" viewBox="0 0 280 190" class="tool-icon">
            <path
              d="M90 42h72l28 28v78a20 20 0 0 1-20 20H90a20 20 0 0 1-20-20V62a20 20 0 0 1 20-20Z"
              fill="#efe5d7"
              stroke="#1d5c55"
              stroke-width="10"
            />
            <path
              d="M162 42v28h28"
              fill="none"
              stroke="#1d5c55"
              stroke-linejoin="round"
              stroke-width="10"
            />
            <path d="M102 98h56" stroke="#f38a55" stroke-linecap="round" stroke-width="10" />
            <path d="M102 122h42" stroke="#f8d7ac" stroke-linecap="round" stroke-width="10" />
            <circle cx="197" cy="128" r="24" fill="#ffb06f" stroke="#1d5c55" stroke-width="10" />
            <path d="M197 112v32" stroke="#fffaf0" stroke-linecap="round" stroke-width="10" />
            <path d="M181 128h32" stroke="#fffaf0" stroke-linecap="round" stroke-width="10" />
          </svg>

          <svg v-else-if="card.id === 'editor'" viewBox="0 0 280 190" class="tool-icon">
            <rect
              x="58"
              y="42"
              width="164"
              height="108"
              rx="28"
              fill="#efe5d7"
              stroke="#1d5c55"
              stroke-width="10"
            />
            <path
              d="M92 74l-20 20 20 20"
              fill="none"
              stroke="#1d5c55"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="12"
            />
            <path
              d="M188 74l20 20-20 20"
              fill="none"
              stroke="#f38a55"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="12"
            />
            <path d="M128 64l-14 60" stroke="#f8d7ac" stroke-linecap="round" stroke-width="12" />
            <rect x="146" y="72" width="24" height="46" rx="10" fill="#ffb06f" />
            <path d="M146 126h60" stroke="#ffffff" stroke-linecap="round" stroke-width="10" />
          </svg>

          <svg v-else viewBox="0 0 280 190" class="tool-icon">
            <rect
              x="52"
              y="46"
              width="176"
              height="98"
              rx="28"
              fill="#efe5d7"
              stroke="#1d5c55"
              stroke-width="10"
            />
            <path d="M88 82h20" stroke="#f38a55" stroke-linecap="round" stroke-width="12" />
            <path d="M122 82h18" stroke="#1d5c55" stroke-linecap="round" stroke-width="12" />
            <path d="M88 108h52" stroke="#f8d7ac" stroke-linecap="round" stroke-width="12" />
            <circle cx="182" cy="82" r="16" fill="none" stroke="#1d5c55" stroke-width="10" />
            <circle cx="208" cy="118" r="12" fill="#ffb06f" />
            <path d="M182 98v20" stroke="#1d5c55" stroke-linecap="round" stroke-width="10" />
            <path d="M194 91l7 7" stroke="#ffffff" stroke-linecap="round" stroke-width="8" />
            <path d="M170 128h22" stroke="#1d5c55" stroke-linecap="round" stroke-width="10" />
          </svg>
        </div>

        <div class="tool-card__copy">
          <h2>{{ card.title }}</h2>
          <p class="tool-card__description">{{ card.description }}</p>
        </div>

        <div class="tool-card__footer">
          <div class="tool-card__tags">
            <span v-for="accent in card.accents" :key="accent">{{ accent }}</span>
          </div>
          <p class="tool-card__detail">{{ card.detail }}</p>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.workspace {
  width: min(1440px, calc(100% - 32px));
  margin: 0 auto;
  padding: 26px 0 40px;
}

.panel {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-2xl);
  background: var(--surface-panel);
  box-shadow: var(--shadow-raised);
  isolation: isolate;
}

.panel::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  border: 1px solid rgba(255, 255, 255, 0.65);
  pointer-events: none;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 22px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.brand-lockup__logo {
  width: min(100%, 198px);
  height: auto;
  flex: none;
  filter: drop-shadow(0 14px 20px rgba(20, 48, 45, 0.14));
}

.brand-lockup__copy {
  display: grid;
  gap: 4px;
}

.brand-lockup__title {
  margin: 0;
  color: var(--text-main);
  font-size: 0.98rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.topbar__status {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.topbar__chip,
.signal-row span,
.tool-card__tags span,
.foundation-footer span,
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 10px 16px;
  border-radius: 999px;
  box-shadow: var(--shadow-pressed);
  color: var(--text-soft);
  font-size: 0.86rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.topbar__chip--accent,
.status-badge {
  color: var(--accent-cool-strong);
}

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

.eyebrow,
.tool-card__eyebrow {
  margin: 0;
  color: var(--accent-coral);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h1 {
  margin: 16px 0 0;
  max-width: 12ch;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(3rem, 4.8vw, 5.8rem);
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
  max-width: 13ch;
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
  min-height: 360px;
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

.tool-card:hover {
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

.tool-card__art {
  display: grid;
  place-items: center;
  min-height: 160px;
  padding: 12px;
  border-radius: calc(var(--radius-2xl) - 10px);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.58), rgba(228, 219, 205, 0.5));
  box-shadow: var(--shadow-pressed);
}

.tool-icon {
  width: min(100%, 240px);
  height: auto;
  filter: drop-shadow(0 18px 24px rgba(17, 55, 51, 0.08));
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
.tool-card__detail {
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

.tool-card__detail {
  max-width: 26ch;
  text-align: right;
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
  .workspace {
    width: min(100% - 20px, 1440px);
    padding-top: 18px;
    padding-bottom: 28px;
  }

  .topbar {
    flex-direction: column;
    align-items: stretch;
  }

  .topbar__status {
    justify-content: flex-start;
  }

  h1,
  .foundation-card h2 {
    max-width: none;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  /* На планшетах и телефонах выстраиваем плитки в один поток, чтобы композиция не ломалась на разной высоте контента. */
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

  .panel {
    border-radius: 28px;
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .brand-lockup__logo {
    width: min(100%, 156px);
  }

  .tool-card {
    min-height: auto;
  }

  .tool-card__meta,
  .tool-card__footer {
    flex-direction: column;
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
    transform: translateY(18px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
