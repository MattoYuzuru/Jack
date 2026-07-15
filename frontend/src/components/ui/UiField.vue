<script setup lang="ts">
import { useId } from 'vue'

const props = defineProps<{
  label: string
  hint?: string
  error?: string
  inputId?: string
}>()

const generatedId = useId()
const controlId = props.inputId ?? generatedId
</script>

<template>
  <div class="ui-field" :class="{ 'ui-field--invalid': error }">
    <label class="ui-field__label" :for="controlId">{{ label }}</label>
    <slot :id="controlId" :described-by="hint || error ? `${controlId}-description` : undefined" />
    <p v-if="hint || error" :id="`${controlId}-description`" class="ui-field__description">
      {{ error || hint }}
    </p>
  </div>
</template>

<style scoped>
.ui-field {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}
.ui-field__label {
  color: var(--text-soft);
  font-size: var(--text-caption);
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.ui-field__description {
  margin: 0;
  color: var(--text-soft);
  font-size: var(--text-caption);
}
.ui-field--invalid .ui-field__description {
  color: #8a4120;
}
</style>
