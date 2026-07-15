<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'info' | 'success' | 'warning' | 'error'
    live?: 'polite' | 'assertive' | 'off'
  }>(),
  { tone: 'info', live: 'polite' },
)
</script>

<template>
  <div
    class="ui-status"
    :class="`ui-status--${tone}`"
    :role="tone === 'error' ? 'alert' : 'status'"
    :aria-live="live"
  >
    <slot />
  </div>
</template>

<style scoped>
.ui-status {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(29, 92, 85, 0.08);
  color: var(--text-main);
}
.ui-status--success {
  background: rgba(62, 132, 103, 0.12);
}
.ui-status--warning {
  background: rgba(255, 207, 143, 0.2);
  color: #72491d;
}
.ui-status--error {
  background: rgba(243, 138, 85, 0.14);
  color: #8a4120;
}
</style>
