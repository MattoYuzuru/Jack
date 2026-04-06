<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import HomeToolIllustration from '../components/HomeToolIllustration.vue'
import { useDevToolsWorkspace } from '../features/dev-tools/composables/useDevToolsWorkspace'
import type { EncodingStrategyId } from '../features/dev-tools/application/encoding-tools'
import type { ValidationFormatId } from '../features/dev-tools/application/validation-tools'

const workspace = useDevToolsWorkspace()
const hashFileInput = ref<HTMLInputElement | null>(null)

const encodingStrategies: Array<{ id: EncodingStrategyId; label: string }> = [
  { id: 'base64', label: 'Base64' },
  { id: 'base64url', label: 'Base64URL' },
  { id: 'url', label: 'URL' },
  { id: 'html', label: 'HTML entities' },
  { id: 'unicode', label: 'Unicode escapes' },
]

const validationFormats: Array<{ id: ValidationFormatId; label: string }> = [
  { id: 'json', label: 'JSON' },
  { id: 'yaml', label: 'YAML' },
  { id: 'xml', label: 'XML' },
  { id: 'env', label: '.env' },
]

const statusCards = computed(() => [
  { value: '6', label: 'toolkits in one route' },
  { value: '24', label: 'daily operations covered' },
  { value: '0', label: 'backend roundtrips needed' },
  { value: 'Local', label: 'state persisted in browser' },
])

const signalPills = [
  'Iteration 07',
  'Frontend-native by design',
  'Clipboard-ready outputs',
  'Persistent local state',
  'JWT claims timeline',
  'SHA + HMAC',
  'UTM cleaner',
  'JSON/YAML/XML/.env validators',
  'UUID + ULID',
  'Timestamp converter',
]

function triggerHashFilePicker(): void {
  hashFileInput.value?.click()
}

function onHashFileSelected(event: Event): void {
  const input = event.target as HTMLInputElement | null
  workspace.setHashFile(input?.files?.[0] ?? null)
  if (input) {
    input.value = ''
  }
}
</script>

<template>
  <main class="workspace-shell devtools-workspace">
    <header class="panel-surface app-topbar">
      <div class="brand-lockup">
        <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
        <div class="brand-lockup__copy">
          <p class="eyebrow">Iteration 07 · Dev Tools And Utils</p>
          <p class="brand-lockup__title">Dev Tools Workspace</p>
        </div>
      </div>

      <div class="devtools-topbar__actions">
        <RouterLink class="back-link" to="/">Back Home</RouterLink>
        <span class="chip-pill">Browser-native route</span>
        <span class="chip-pill chip-pill--accent">Instant daily workflows</span>
      </div>
    </header>

    <section class="devtools-hero">
      <article class="panel-surface devtools-hero__copy">
        <p class="eyebrow">Local-First Engineering Utilities</p>
        <h1>
          Dev Tools закрывает ежедневные инженерные мелочи прямо в вебе: encoding lab, JWT
          inspector, hash toolkit, link cleaner, validators и quick helpers.
        </h1>
        <p class="lead">
          В этой итерации архитектура выбрана осознанно: инструменты мгновенные, тексто- и
          URL-центричные, не дают выгоды от backend queue/artifact orchestration и поэтому живут в
          браузере. Это сохраняет ощущение локального toolbox, но остаётся в том же визуальном и
          продуктово-модульном каркасе Jack.
        </p>

        <div class="signal-row">
          <span v-for="pill in signalPills" :key="pill" class="chip-pill chip-pill--compact">
            {{ pill }}
          </span>
        </div>
      </article>

      <article class="panel-surface devtools-hero__aside">
        <div class="devtools-hero__art">
          <HomeToolIllustration id="devtools" />
        </div>

        <div class="devtools-hero__notes">
          <p class="eyebrow">System Notes</p>
          <h2>Новый маршрут не спорит с processing-platform, а закрывает другой класс задач.</h2>
          <p>
            Viewer, converter, compression, PDF toolkit и editor остаются backend-first там, где
            нужны artifacts, retries и единый source of truth. Dev tools, наоборот, должны быть
            мгновенными, копируемыми и безопасно локальными.
          </p>
        </div>
      </article>
    </section>

    <section class="devtools-status-grid" aria-label="Status overview">
      <article v-for="card in statusCards" :key="card.label" class="panel-surface devtools-stat">
        <strong>{{ card.value }}</strong>
        <span>{{ card.label }}</span>
      </article>
    </section>

    <section class="devtools-tool-grid" aria-label="Dev tool catalog">
      <button
        v-for="tool in workspace.tools"
        :key="tool.id"
        class="panel-surface devtools-tool-card"
        :class="{ 'devtools-tool-card--active': workspace.activeToolId.value === tool.id }"
        type="button"
        @click="workspace.selectTool(tool.id)"
      >
        <div class="devtools-tool-card__meta">
          <p class="devtools-tool-card__eyebrow">{{ tool.label }}</p>
          <div class="devtools-tool-card__tags">
            <span v-for="accent in tool.accents" :key="accent" class="chip-pill chip-pill--compact">
              {{ accent }}
            </span>
          </div>
        </div>
        <div class="devtools-tool-card__copy">
          <h2>{{ tool.title }}</h2>
          <p>{{ tool.description }}</p>
          <span class="devtools-tool-card__detail">{{ tool.detail }}</span>
        </div>
      </button>
    </section>

    <section class="devtools-main-grid">
      <article class="panel-surface devtools-panel">
        <div class="devtools-panel__header">
          <div>
            <p class="eyebrow">Input Workspace</p>
            <h2>{{ workspace.activeTool.value?.title }}</h2>
          </div>
          <span class="chip-pill chip-pill--accent">{{
            workspace.activeTool.value?.description
          }}</span>
        </div>

        <div v-if="workspace.activeToolId.value === 'encoding'" class="devtools-stack">
          <div class="devtools-field-row">
            <label class="devtools-field">
              <span>Strategy</span>
              <select v-model="workspace.encodingStrategyId.value">
                <option v-for="option in encodingStrategies" :key="option.id" :value="option.id">
                  {{ option.label }}
                </option>
              </select>
            </label>

            <div class="devtools-toggle-group">
              <button
                class="action-button"
                :class="{ 'action-button--accent': workspace.encodingMode.value === 'encode' }"
                type="button"
                @click="workspace.encodingMode.value = 'encode'"
              >
                Encode
              </button>
              <button
                class="action-button"
                :class="{ 'action-button--accent': workspace.encodingMode.value === 'decode' }"
                type="button"
                @click="workspace.encodingMode.value = 'decode'"
              >
                Decode
              </button>
            </div>
          </div>

          <label class="devtools-field">
            <span>Input</span>
            <textarea
              v-model="workspace.encodingInput.value"
              rows="12"
              spellcheck="false"
              placeholder="Paste text to encode or decode"
            />
          </label>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'jwt'" class="devtools-stack">
          <label class="devtools-field">
            <span>JWT token</span>
            <textarea
              v-model="workspace.jwtInput.value"
              rows="12"
              spellcheck="false"
              placeholder="eyJhbGciOi..."
            />
          </label>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'hash'" class="devtools-stack">
          <div class="devtools-field-row">
            <div class="devtools-toggle-group">
              <button
                class="action-button"
                :class="{ 'action-button--accent': workspace.hashSourceMode.value === 'text' }"
                type="button"
                @click="workspace.hashSourceMode.value = 'text'"
              >
                Text
              </button>
              <button
                class="action-button"
                :class="{ 'action-button--accent': workspace.hashSourceMode.value === 'file' }"
                type="button"
                @click="workspace.hashSourceMode.value = 'file'"
              >
                File
              </button>
            </div>

            <label class="devtools-field">
              <span>HMAC secret</span>
              <input
                v-model="workspace.hashSecret.value"
                type="password"
                spellcheck="false"
                placeholder="Optional shared secret"
              />
            </label>
          </div>

          <label v-if="workspace.hashSourceMode.value === 'text'" class="devtools-field">
            <span>Payload</span>
            <textarea
              v-model="workspace.hashTextInput.value"
              rows="10"
              spellcheck="false"
              placeholder="Payload to hash"
            />
          </label>

          <div v-else class="devtools-file-picker">
            <p class="devtools-file-picker__title">
              {{ workspace.hashFile.value?.name ?? 'No file selected' }}
            </p>
            <p class="devtools-file-picker__detail">
              {{ workspace.hashFile.value?.type || 'Pick any local file to compute digests.' }}
            </p>
            <div class="devtools-file-picker__actions">
              <button
                class="action-button action-button--accent"
                type="button"
                @click="triggerHashFilePicker"
              >
                Choose File
              </button>
              <button
                class="action-button"
                type="button"
                :disabled="!workspace.hashFile.value"
                @click="workspace.clearHashFile"
              >
                Clear
              </button>
            </div>
            <input
              ref="hashFileInput"
              class="devtools-hidden-input"
              type="file"
              @change="onHashFileSelected"
            />
          </div>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'link'" class="devtools-stack">
          <label class="devtools-field">
            <span>URL</span>
            <textarea
              v-model="workspace.linkInput.value"
              rows="8"
              spellcheck="false"
              placeholder="example.com/path?utm_source=..."
            />
          </label>

          <div class="devtools-checkbox-grid">
            <label class="devtools-checkbox">
              <input v-model="workspace.linkOptions.stripTracking" type="checkbox" />
              <span>Strip tracking params</span>
            </label>
            <label class="devtools-checkbox">
              <input v-model="workspace.linkOptions.sortParams" type="checkbox" />
              <span>Sort query params</span>
            </label>
            <label class="devtools-checkbox">
              <input v-model="workspace.linkOptions.removeFragment" type="checkbox" />
              <span>Remove fragment</span>
            </label>
          </div>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'validator'" class="devtools-stack">
          <div class="devtools-toggle-group">
            <button
              v-for="format in validationFormats"
              :key="format.id"
              class="action-button"
              :class="{ 'action-button--accent': workspace.validationFormatId.value === format.id }"
              type="button"
              @click="workspace.validationFormatId.value = format.id"
            >
              {{ format.label }}
            </button>
          </div>

          <label class="devtools-field">
            <span>Input</span>
            <textarea
              v-model="workspace.validationInput.value"
              rows="12"
              spellcheck="false"
              placeholder="Paste JSON, YAML, XML or .env payload"
            />
          </label>
        </div>

        <div v-else class="devtools-stack">
          <div class="devtools-quick-grid">
            <article class="devtools-quick-card">
              <div class="devtools-quick-card__header">
                <div>
                  <p class="eyebrow">UUID</p>
                  <h3>{{ workspace.quickUuid.value }}</h3>
                </div>
                <button class="action-button" type="button" @click="workspace.regenerateUuid">
                  New UUID
                </button>
              </div>
            </article>

            <article class="devtools-quick-card">
              <div class="devtools-quick-card__header">
                <div>
                  <p class="eyebrow">ULID</p>
                  <h3>{{ workspace.quickUlid.value }}</h3>
                </div>
                <button class="action-button" type="button" @click="workspace.regenerateUlid">
                  New ULID
                </button>
              </div>
            </article>
          </div>

          <div class="devtools-field-row">
            <label class="devtools-field">
              <span>Timestamp</span>
              <input
                v-model="workspace.timestampInput.value"
                type="text"
                spellcheck="false"
                placeholder="1712400000 or 2026-04-06T10:15:00Z"
              />
            </label>
            <button
              class="action-button action-button--accent"
              type="button"
              @click="workspace.useCurrentTimestamp"
            >
              Use Now
            </button>
          </div>

          <div class="devtools-field-row">
            <label class="devtools-field">
              <span>Basic Auth user</span>
              <input v-model="workspace.basicAuthUsername.value" type="text" spellcheck="false" />
            </label>
            <label class="devtools-field">
              <span>Password</span>
              <input
                v-model="workspace.basicAuthPassword.value"
                type="password"
                spellcheck="false"
              />
            </label>
          </div>
        </div>
      </article>

      <article class="panel-surface devtools-panel devtools-panel--output">
        <div class="devtools-panel__header">
          <div>
            <p class="eyebrow">Output & Diagnostics</p>
            <h2>{{ workspace.activeTool.value?.title }}</h2>
          </div>
          <span class="chip-pill">{{
            workspace.actionMessage.value || 'Ready for copy / export'
          }}</span>
        </div>

        <div v-if="workspace.activeToolId.value === 'encoding'" class="devtools-stack">
          <div class="devtools-output-actions">
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="!workspace.encodingResult.value.output"
              @click="workspace.copyText(workspace.encodingResult.value.output, 'Encoding result')"
            >
              Copy Output
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.encodingResult.value.output"
              @click="
                workspace.downloadText(
                  'jack-encoding-result.txt',
                  workspace.encodingResult.value.output,
                )
              "
            >
              Download Output
            </button>
          </div>

          <p
            v-if="workspace.encodingResult.value.error"
            class="devtools-message devtools-message--error"
          >
            {{ workspace.encodingResult.value.error }}
          </p>
          <template v-else>
            <label class="devtools-field">
              <span>Output</span>
              <textarea
                :value="workspace.encodingResult.value.output"
                rows="12"
                readonly
                spellcheck="false"
              />
            </label>

            <div class="devtools-facts-grid">
              <article
                v-for="fact in workspace.encodingResult.value.facts"
                :key="fact.label"
                class="devtools-fact"
              >
                <strong>{{ fact.value }}</strong>
                <span>{{ fact.label }}</span>
              </article>
            </div>

            <ul v-if="workspace.encodingResult.value.warnings.length" class="devtools-issue-list">
              <li v-for="warning in workspace.encodingResult.value.warnings" :key="warning">
                {{ warning }}
              </li>
            </ul>
          </template>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'jwt'" class="devtools-stack">
          <p
            v-if="workspace.jwtResult.value.error"
            class="devtools-message devtools-message--error"
          >
            {{ workspace.jwtResult.value.error }}
          </p>
          <template v-else>
            <div class="devtools-output-actions">
              <button
                class="action-button action-button--accent"
                type="button"
                @click="workspace.copyText(workspace.jwtResult.value.bearerHeader, 'Bearer header')"
              >
                Copy Bearer Header
              </button>
              <button
                class="action-button"
                type="button"
                @click="
                  workspace.downloadText(
                    'jack-jwt-payload.json',
                    workspace.jwtResult.value.payloadPretty,
                    'application/json;charset=utf-8',
                  )
                "
              >
                Download Payload
              </button>
            </div>

            <div class="devtools-pre-grid">
              <label class="devtools-field">
                <span>Header</span>
                <textarea
                  :value="workspace.jwtResult.value.headerPretty"
                  rows="8"
                  readonly
                  spellcheck="false"
                />
              </label>
              <label class="devtools-field">
                <span>Payload</span>
                <textarea
                  :value="workspace.jwtResult.value.payloadPretty"
                  rows="8"
                  readonly
                  spellcheck="false"
                />
              </label>
            </div>

            <div class="devtools-facts-grid">
              <article
                v-for="fact in workspace.jwtResult.value.facts"
                :key="fact.label"
                class="devtools-fact"
              >
                <strong>{{ fact.value }}</strong>
                <span>{{ fact.label }}</span>
              </article>
            </div>

            <ul class="devtools-issue-list">
              <li v-for="warning in workspace.jwtResult.value.warnings" :key="warning">
                {{ warning }}
              </li>
            </ul>

            <div class="devtools-claims-card">
              <div class="devtools-claims-card__header">
                <h3>Claim Inspector</h3>
                <span class="chip-pill chip-pill--compact"
                  >{{ workspace.jwtResult.value.claims.length }} claims</span
                >
              </div>
              <div class="devtools-claims-list">
                <article
                  v-for="claim in workspace.jwtResult.value.claims"
                  :key="claim.name"
                  class="devtools-claim"
                >
                  <strong>{{ claim.name }}</strong>
                  <span>{{ claim.rawValue }}</span>
                  <small v-if="claim.resolvedValue">{{ claim.resolvedValue }}</small>
                </article>
              </div>
            </div>
          </template>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'hash'" class="devtools-stack">
          <p v-if="workspace.isHashing.value" class="devtools-message">Computing digests...</p>
          <p
            v-else-if="workspace.hashErrorMessage.value"
            class="devtools-message devtools-message--error"
          >
            {{ workspace.hashErrorMessage.value }}
          </p>
          <p
            v-else-if="workspace.hashSourceMode.value === 'file' && !workspace.hashFile.value"
            class="devtools-message"
          >
            Choose a file to build the digest table.
          </p>
          <template v-else-if="workspace.hashReport.value">
            <div class="devtools-facts-grid">
              <article
                v-for="fact in workspace.hashReport.value.facts"
                :key="fact.label"
                class="devtools-fact"
              >
                <strong>{{ fact.value }}</strong>
                <span>{{ fact.label }}</span>
              </article>
            </div>

            <div class="devtools-hash-table">
              <div class="devtools-hash-table__header">
                <h3>Digests</h3>
                <span class="chip-pill chip-pill--compact">Web Crypto</span>
              </div>
              <article
                v-for="digest in workspace.hashReport.value.digests"
                :key="digest.id"
                class="devtools-hash-row"
              >
                <div>
                  <strong>{{ digest.label }}</strong>
                  <span>{{ digest.value }}</span>
                </div>
                <button
                  class="action-button"
                  type="button"
                  @click="workspace.copyText(digest.value, digest.label)"
                >
                  Copy
                </button>
              </article>
            </div>

            <div v-if="workspace.hashReport.value.hmacDigests.length" class="devtools-hash-table">
              <div class="devtools-hash-table__header">
                <h3>HMAC</h3>
                <span class="chip-pill chip-pill--compact chip-pill--accent">Secret enabled</span>
              </div>
              <article
                v-for="digest in workspace.hashReport.value.hmacDigests"
                :key="digest.id"
                class="devtools-hash-row"
              >
                <div>
                  <strong>{{ digest.label }}</strong>
                  <span>{{ digest.value }}</span>
                </div>
                <button
                  class="action-button"
                  type="button"
                  @click="workspace.copyText(digest.value, digest.label)"
                >
                  Copy
                </button>
              </article>
            </div>
          </template>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'link'" class="devtools-stack">
          <p
            v-if="workspace.linkResult.value.error"
            class="devtools-message devtools-message--error"
          >
            {{ workspace.linkResult.value.error }}
          </p>
          <template v-else>
            <div class="devtools-output-actions">
              <button
                class="action-button action-button--accent"
                type="button"
                @click="workspace.copyText(workspace.linkResult.value.cleanedUrl, 'Clean URL')"
              >
                Copy Clean URL
              </button>
              <button
                class="action-button"
                type="button"
                @click="
                  workspace.copyText(workspace.linkResult.value.normalizedUrl, 'Normalized URL')
                "
              >
                Copy Normalized URL
              </button>
            </div>

            <label class="devtools-field">
              <span>Normalized URL</span>
              <textarea
                :value="workspace.linkResult.value.normalizedUrl"
                rows="4"
                readonly
                spellcheck="false"
              />
            </label>
            <label class="devtools-field">
              <span>Share-friendly URL</span>
              <textarea
                :value="workspace.linkResult.value.cleanedUrl"
                rows="4"
                readonly
                spellcheck="false"
              />
            </label>

            <div class="devtools-facts-grid">
              <article
                v-for="fact in workspace.linkResult.value.facts"
                :key="fact.label"
                class="devtools-fact"
              >
                <strong>{{ fact.value }}</strong>
                <span>{{ fact.label }}</span>
              </article>
            </div>

            <ul v-if="workspace.linkResult.value.warnings.length" class="devtools-issue-list">
              <li v-for="warning in workspace.linkResult.value.warnings" :key="warning">
                {{ warning }}
              </li>
            </ul>

            <div class="devtools-hash-table">
              <div class="devtools-hash-table__header">
                <h3>Query params</h3>
                <span class="chip-pill chip-pill--compact">
                  {{ workspace.linkResult.value.queryEntries.length }} entries
                </span>
              </div>
              <article
                v-for="entry in workspace.linkResult.value.queryEntries"
                :key="`${entry.key}:${entry.value}`"
                class="devtools-hash-row"
              >
                <div>
                  <strong>{{ entry.key }}</strong>
                  <span>{{ entry.value || 'empty' }}</span>
                </div>
                <span
                  class="chip-pill chip-pill--compact"
                  :class="{ 'chip-pill--accent': entry.status === 'removed' }"
                >
                  {{ entry.status }}
                </span>
              </article>
            </div>
          </template>
        </div>

        <div v-else-if="workspace.activeToolId.value === 'validator'" class="devtools-stack">
          <div class="devtools-output-actions">
            <button
              class="action-button action-button--accent"
              type="button"
              :disabled="!workspace.validationResult.value.valid"
              @click="
                workspace.copyText(
                  workspace.validationResult.value.normalized,
                  'Normalized payload',
                )
              "
            >
              Copy Normalized
            </button>
            <button
              class="action-button"
              type="button"
              :disabled="!workspace.validationResult.value.valid"
              @click="
                workspace.downloadText(
                  `jack-validator-${workspace.validationFormatId.value}.txt`,
                  workspace.validationResult.value.normalized,
                )
              "
            >
              Download Normalized
            </button>
          </div>

          <div class="devtools-facts-grid">
            <article
              v-for="fact in workspace.validationResult.value.facts"
              :key="fact.label"
              class="devtools-fact"
            >
              <strong>{{ fact.value }}</strong>
              <span>{{ fact.label }}</span>
            </article>
          </div>

          <ul
            class="devtools-issue-list"
            :class="{
              'devtools-issue-list--clean': !workspace.validationResult.value.issues.length,
            }"
          >
            <li v-if="!workspace.validationResult.value.issues.length">Payload looks valid.</li>
            <li
              v-for="issue in workspace.validationResult.value.issues"
              :key="`${issue.code}:${issue.message}`"
            >
              <strong>{{ issue.code }}</strong>
              <span>{{ issue.message }}</span>
            </li>
          </ul>

          <label v-if="workspace.validationResult.value.valid" class="devtools-field">
            <span>Normalized output</span>
            <textarea
              :value="workspace.validationResult.value.normalized"
              rows="12"
              readonly
              spellcheck="false"
            />
          </label>
        </div>

        <div v-else class="devtools-stack">
          <div class="devtools-quick-grid">
            <article class="devtools-quick-card">
              <p class="eyebrow">UUID</p>
              <h3>{{ workspace.quickUuid.value }}</h3>
              <button
                class="action-button action-button--accent"
                type="button"
                @click="workspace.copyText(workspace.quickUuid.value, 'UUID')"
              >
                Copy UUID
              </button>
            </article>

            <article class="devtools-quick-card">
              <p class="eyebrow">ULID</p>
              <h3>{{ workspace.quickUlid.value }}</h3>
              <button
                class="action-button action-button--accent"
                type="button"
                @click="workspace.copyText(workspace.quickUlid.value, 'ULID')"
              >
                Copy ULID
              </button>
            </article>
          </div>

          <div class="devtools-hash-table">
            <div class="devtools-hash-table__header">
              <h3>Timestamp Converter</h3>
              <span
                class="chip-pill chip-pill--compact"
                :class="{ 'chip-pill--accent': workspace.timestampResult.value.ok }"
              >
                {{ workspace.timestampResult.value.ok ? 'Parsed' : 'Input needed' }}
              </span>
            </div>
            <p
              v-if="workspace.timestampResult.value.error"
              class="devtools-message devtools-message--error"
            >
              {{ workspace.timestampResult.value.error }}
            </p>
            <template v-else>
              <article class="devtools-hash-row">
                <div>
                  <strong>UTC ISO</strong>
                  <span>{{ workspace.timestampResult.value.isoUtc }}</span>
                </div>
                <button
                  class="action-button"
                  type="button"
                  @click="workspace.copyText(workspace.timestampResult.value.isoUtc, 'UTC ISO')"
                >
                  Copy
                </button>
              </article>
              <article class="devtools-hash-row">
                <div>
                  <strong>Local time</strong>
                  <span>{{ workspace.timestampResult.value.localTime }}</span>
                </div>
                <button
                  class="action-button"
                  type="button"
                  @click="
                    workspace.copyText(workspace.timestampResult.value.localTime, 'Local time')
                  "
                >
                  Copy
                </button>
              </article>
              <article class="devtools-hash-row">
                <div>
                  <strong>Epoch seconds</strong>
                  <span>{{ workspace.timestampResult.value.epochSeconds }}</span>
                </div>
                <button
                  class="action-button"
                  type="button"
                  @click="
                    workspace.copyText(
                      workspace.timestampResult.value.epochSeconds,
                      'Epoch seconds',
                    )
                  "
                >
                  Copy
                </button>
              </article>
              <article class="devtools-hash-row">
                <div>
                  <strong>Epoch milliseconds</strong>
                  <span>{{ workspace.timestampResult.value.epochMilliseconds }}</span>
                </div>
                <button
                  class="action-button"
                  type="button"
                  @click="
                    workspace.copyText(
                      workspace.timestampResult.value.epochMilliseconds,
                      'Epoch milliseconds',
                    )
                  "
                >
                  Copy
                </button>
              </article>
            </template>
          </div>

          <div class="devtools-hash-table">
            <div class="devtools-hash-table__header">
              <h3>Basic Auth Helper</h3>
              <span class="chip-pill chip-pill--compact">Header + curl</span>
            </div>
            <article class="devtools-hash-row">
              <div>
                <strong>Authorization header</strong>
                <span>{{ workspace.basicAuthResult.value.header }}</span>
              </div>
              <button
                class="action-button"
                type="button"
                @click="
                  workspace.copyText(workspace.basicAuthResult.value.header, 'Basic auth header')
                "
              >
                Copy
              </button>
            </article>
            <article class="devtools-hash-row">
              <div>
                <strong>curl snippet</strong>
                <span>{{ workspace.basicAuthResult.value.curlSnippet }}</span>
              </div>
              <button
                class="action-button"
                type="button"
                @click="
                  workspace.copyText(workspace.basicAuthResult.value.curlSnippet, 'curl snippet')
                "
              >
                Copy
              </button>
            </article>
          </div>
        </div>
      </article>
    </section>
  </main>
</template>

<style scoped>
.devtools-topbar__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.devtools-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.82fr);
  gap: 22px;
  margin-top: 22px;
}

.devtools-hero__copy,
.devtools-hero__aside,
.devtools-panel,
.devtools-stat {
  padding: 24px;
}

.lead {
  margin: 16px 0 0;
  max-width: 70ch;
  color: var(--text-soft);
  font-size: 1rem;
}

.signal-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.devtools-hero__aside {
  display: grid;
  gap: 18px;
  align-content: start;
  background:
    radial-gradient(circle at top left, rgba(255, 207, 143, 0.32), transparent 30%),
    var(--surface-panel);
}

.devtools-hero__art {
  display: grid;
  place-items: center;
  min-height: 190px;
  border-radius: calc(var(--radius-xl) - 6px);
  background: linear-gradient(145deg, rgba(249, 242, 231, 0.95), rgba(230, 220, 206, 0.94));
  box-shadow: var(--shadow-pressed);
}

.devtools-hero__notes h2,
.devtools-panel__header h2,
.devtools-tool-card__copy h2 {
  margin: 8px 0 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 2vw, 1.85rem);
}

.devtools-hero__notes p:not(.eyebrow) {
  margin: 12px 0 0;
  color: var(--text-soft);
}

.devtools-status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
}

.devtools-stat {
  display: grid;
  gap: 8px;
  min-height: 140px;
}

.devtools-stat strong,
.devtools-fact strong {
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 2vw, 2rem);
}

.devtools-stat span,
.devtools-fact span,
.devtools-tool-card__copy p,
.devtools-tool-card__detail,
.devtools-hash-row span,
.devtools-message,
.devtools-file-picker__detail {
  color: var(--text-soft);
}

.devtools-tool-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
}

.devtools-tool-card {
  display: grid;
  gap: 16px;
  padding: 22px;
  border: 0;
  text-align: left;
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    border-color 180ms ease;
}

.devtools-tool-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-floating);
}

.devtools-tool-card--active {
  background:
    radial-gradient(circle at top left, rgba(255, 207, 143, 0.22), transparent 36%),
    var(--surface-panel);
}

.devtools-tool-card__meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.devtools-tool-card__eyebrow {
  margin: 0;
  color: var(--accent-coral);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.devtools-tool-card__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.devtools-tool-card__detail {
  margin-top: 10px;
  display: block;
}

.devtools-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.98fr) minmax(0, 1.02fr);
  gap: 22px;
  margin-top: 22px;
  align-items: start;
}

.devtools-panel {
  display: grid;
  gap: 20px;
}

.devtools-panel--output {
  background:
    radial-gradient(circle at top right, rgba(29, 92, 85, 0.12), transparent 24%),
    var(--surface-panel);
}

.devtools-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.devtools-stack {
  display: grid;
  gap: 18px;
}

.devtools-field-row,
.devtools-output-actions,
.devtools-toggle-group,
.devtools-file-picker__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.devtools-field {
  display: grid;
  gap: 8px;
  flex: 1 1 240px;
}

.devtools-field span {
  color: var(--text-main);
  font-size: 0.9rem;
  font-weight: 700;
}

.devtools-field input,
.devtools-field textarea,
.devtools-field select {
  width: 100%;
  border: 1px solid rgba(29, 92, 85, 0.12);
  border-radius: 22px;
  background: linear-gradient(145deg, rgba(252, 247, 238, 0.96), rgba(233, 223, 210, 0.92));
  box-shadow: var(--shadow-pressed);
  color: var(--text-strong);
  padding: 14px 16px;
  resize: vertical;
}

.devtools-field textarea {
  min-height: 150px;
  font-family: 'SFMono-Regular', 'Menlo', monospace;
  line-height: 1.45;
}

.devtools-field input:focus-visible,
.devtools-field textarea:focus-visible,
.devtools-field select:focus-visible {
  outline: 2px solid rgba(29, 92, 85, 0.28);
  outline-offset: 3px;
}

.devtools-file-picker,
.devtools-quick-card,
.devtools-fact,
.devtools-claims-card,
.devtools-hash-table {
  padding: 18px;
  border-radius: calc(var(--radius-xl) - 6px);
  background: linear-gradient(145deg, rgba(249, 242, 231, 0.95), rgba(229, 219, 206, 0.92));
  box-shadow: var(--shadow-pressed);
}

.devtools-file-picker__title,
.devtools-quick-card h3,
.devtools-hash-table__header h3,
.devtools-claims-card__header h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
  font-size: 1.1rem;
}

.devtools-file-picker__detail {
  margin: 8px 0 0;
}

.devtools-hidden-input {
  display: none;
}

.devtools-checkbox-grid,
.devtools-facts-grid,
.devtools-quick-grid,
.devtools-pre-grid {
  display: grid;
  gap: 12px;
}

.devtools-checkbox-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.devtools-checkbox {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(249, 242, 231, 0.95), rgba(229, 219, 206, 0.92));
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
  font-weight: 700;
}

.devtools-facts-grid,
.devtools-quick-grid,
.devtools-pre-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.devtools-fact {
  display: grid;
  gap: 8px;
}

.devtools-issue-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 20px;
  color: var(--text-main);
}

.devtools-issue-list li {
  display: grid;
  gap: 4px;
}

.devtools-issue-list--clean {
  color: var(--accent-cool-strong);
}

.devtools-message {
  margin: 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.38);
}

.devtools-message--error {
  color: #8e3d1e;
  background: rgba(243, 138, 85, 0.14);
}

.devtools-claims-card__header,
.devtools-hash-table__header,
.devtools-quick-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.devtools-claims-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.devtools-claim,
.devtools-hash-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-top: 1px solid rgba(29, 92, 85, 0.12);
}

.devtools-claim:first-child,
.devtools-hash-row:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.devtools-claim {
  display: grid;
}

.devtools-claim strong,
.devtools-hash-row strong {
  color: var(--text-strong);
}

.devtools-claim small {
  color: var(--accent-cool);
}

.devtools-hash-table {
  display: grid;
  gap: 12px;
}

.devtools-hash-row span {
  display: block;
  max-width: 42ch;
  word-break: break-word;
}

@media (max-width: 1180px) {
  .devtools-hero,
  .devtools-main-grid {
    grid-template-columns: 1fr;
  }

  .devtools-status-grid,
  .devtools-tool-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .devtools-checkbox-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .devtools-topbar__actions,
  .devtools-panel__header,
  .devtools-tool-card__meta,
  .devtools-tool-card__tags,
  .devtools-claims-card__header,
  .devtools-hash-table__header,
  .devtools-quick-card__header {
    display: grid;
  }

  .devtools-status-grid,
  .devtools-tool-grid,
  .devtools-facts-grid,
  .devtools-quick-grid,
  .devtools-pre-grid {
    grid-template-columns: 1fr;
  }

  .devtools-claim,
  .devtools-hash-row {
    display: grid;
  }
}
</style>
