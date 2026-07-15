<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    accept?: string
    disabled?: boolean
    label: string
  }>(),
  { accept: '', disabled: false },
)

const emit = defineEmits<{
  file: [file: File]
}>()

const input = ref<HTMLInputElement | null>(null)
const active = ref(false)

function open(): void {
  if (!props.disabled) input.value?.click()
}

function emitFirst(files: FileList | null): void {
  const file = files?.[0]
  if (file && !props.disabled) emit('file', file)
}

function onDrop(event: DragEvent): void {
  active.value = false
  emitFirst(event.dataTransfer?.files ?? null)
}
</script>

<template>
  <div
    class="ui-dropzone"
    :class="{ 'ui-dropzone--active': active, 'ui-dropzone--disabled': disabled }"
    role="button"
    :tabindex="disabled ? -1 : 0"
    :aria-disabled="disabled"
    :aria-label="label"
    @click="open"
    @keydown.enter.prevent="open"
    @keydown.space.prevent="open"
    @dragenter.prevent="active = true"
    @dragover.prevent="active = true"
    @dragleave.prevent="active = false"
    @drop.prevent="onDrop"
  >
    <slot :active="active" />
    <input
      ref="input"
      class="visually-hidden"
      type="file"
      :accept="accept"
      tabindex="-1"
      @change="emitFirst(($event.target as HTMLInputElement).files)"
    />
  </div>
</template>

<style scoped>
.ui-dropzone {
  display: grid;
  min-height: 180px;
  place-items: center;
  padding: var(--space-6);
  border: 1px dashed rgba(29, 92, 85, 0.3);
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 243, 0.56);
  box-shadow: var(--elevation-inset);
  cursor: pointer;
  transition:
    border-color var(--motion-fast),
    transform var(--motion-fast);
}
.ui-dropzone:hover,
.ui-dropzone:focus-visible,
.ui-dropzone--active {
  outline: var(--focus-ring);
  outline-offset: var(--focus-offset);
  border-color: var(--accent-cool);
  transform: translateY(-2px);
}
.ui-dropzone--disabled {
  cursor: not-allowed;
  opacity: 0.58;
  transform: none;
}
</style>
