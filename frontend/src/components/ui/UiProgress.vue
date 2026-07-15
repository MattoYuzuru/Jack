<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    value?: number
    label: string
  }>(),
  { value: 0 },
)

const normalizedValue = () => Math.min(100, Math.max(0, props.value))
</script>

<template>
  <div class="ui-progress">
    <div class="ui-progress__labels">
      <span>{{ label }}</span
      ><span>{{ normalizedValue() }}%</span>
    </div>
    <progress :value="normalizedValue()" max="100">{{ normalizedValue() }}%</progress>
  </div>
</template>

<style scoped>
.ui-progress {
  display: grid;
  gap: var(--space-2);
}
.ui-progress__labels {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--text-soft);
  font-size: var(--text-label);
}
.ui-progress progress {
  width: 100%;
  height: 10px;
  overflow: hidden;
  border: 0;
  border-radius: var(--radius-pill);
  background: rgba(29, 92, 85, 0.12);
}
.ui-progress progress::-webkit-progress-bar {
  background: rgba(29, 92, 85, 0.12);
}
.ui-progress progress::-webkit-progress-value {
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent-coral), var(--accent-cool));
}
.ui-progress progress::-moz-progress-bar {
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent-coral), var(--accent-cool));
}
</style>
