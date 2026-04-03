<template>
  <main class="shell">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">Jack of all trades</p>
        <h1>One workspace for files, text tools, and daily developer utilities.</h1>
        <p class="lead">
          The repository currently contains the bootstrap layer only: a Spring Boot backend, a Vue
          frontend, reusable brand assets, and a roadmap designed for iterative delivery.
        </p>

        <div class="pill-row">
          <span>Spring Boot 4.0.5</span>
          <span>Java 26</span>
          <span>Vue 3</span>
          <span>Roadmap-driven</span>
        </div>
      </div>

      <div class="hero-card">
        <img class="logo" src="/logo.svg" alt="Jack logo" />
      </div>
    </section>

    <section class="grid">
      <article class="card">
        <h2>Workspace Scope</h2>
        <p>
          File viewing, conversion, compression, PDF flows, live editors, validators, hashes,
          encoders, decoders, JWT utilities, and more.
        </p>
      </article>

      <article class="card">
        <h2>Repo Shape</h2>
        <p>
          <strong>backend</strong> for Spring Boot, <strong>frontend</strong> for Vue, and
          <strong>assets/brand</strong> for the shared logo and favicon source files.
        </p>
      </article>

      <article class="card">
        <h2>Next Iterations</h2>
        <p>
          Future work can start from a clean feature branch and implement one roadmap slice at a
          time, with tests, docs, and MR-ready delivery.
        </p>
      </article>
    </section>
  </main>
</template>

<style scoped>
.shell {
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 48px 0 64px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 420px);
  gap: 24px;
  align-items: stretch;
}

.hero-copy,
.hero-card,
.card {
  border: 1px solid rgba(20, 48, 45, 0.08);
  border-radius: 28px;
  background: rgba(255, 252, 246, 0.82);
  box-shadow: 0 20px 60px rgba(18, 48, 45, 0.1);
  backdrop-filter: blur(12px);
}

.hero-copy {
  padding: 36px;
  animation: rise 0.7s ease-out both;
}

.eyebrow {
  margin: 0 0 12px;
  color: #a95d31;
  font-size: 0.9rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  max-width: 12ch;
  color: #102426;
  font-size: clamp(2.6rem, 4vw, 4.8rem);
  line-height: 0.95;
}

.lead {
  margin: 22px 0 0;
  max-width: 60ch;
  color: #425654;
  font-size: 1.05rem;
}

.pill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.pill-row span {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(18, 48, 45, 0.06);
  color: #14302d;
  font-weight: 600;
}

.hero-card {
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at top, rgba(255, 207, 143, 0.45), transparent 36%),
    linear-gradient(180deg, rgba(255, 249, 240, 0.94), rgba(249, 244, 236, 0.88));
  animation: rise 0.8s ease-out 0.08s both;
}

.logo {
  width: min(100%, 340px);
  height: auto;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.card {
  padding: 28px;
  animation: rise 0.8s ease-out both;
}

.card:nth-child(2) {
  animation-delay: 0.12s;
}

.card:nth-child(3) {
  animation-delay: 0.18s;
}

.card h2 {
  margin: 0 0 12px;
  color: #14302d;
  font-size: 1.05rem;
}

.card p {
  margin: 0;
  color: #4a5f5d;
}

@media (max-width: 900px) {
  .hero,
  .grid {
    grid-template-columns: 1fr;
  }

  h1 {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .shell {
    width: min(100% - 20px, 1120px);
    padding-top: 20px;
    padding-bottom: 36px;
  }

  .hero-copy,
  .hero-card,
  .card {
    border-radius: 22px;
  }

  .hero-copy,
  .hero-card,
  .card {
    padding: 22px;
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
