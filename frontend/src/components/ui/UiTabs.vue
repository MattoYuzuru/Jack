<script setup lang="ts">
interface UiTabItem {
  id: string
  label: string
}

const props = defineProps<{
  modelValue: string
  items: UiTabItem[]
  label: string
  idPrefix: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function moveFocus(event: KeyboardEvent, index: number): void {
  const direction = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1
  if (!['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    return
  }
  event.preventDefault()
  const nextIndex =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? props.items.length - 1
        : (index + direction + props.items.length) % props.items.length
  const nextItem = props.items[nextIndex]
  if (nextItem) {
    emit('update:modelValue', nextItem.id)
    requestAnimationFrame(() =>
      document.getElementById(`${props.idPrefix}-tab-${nextItem.id}`)?.focus(),
    )
  }
}
</script>

<template>
  <div class="ui-tabs" role="tablist" :aria-label="label">
    <button
      v-for="(item, index) in items"
      :id="`${idPrefix}-tab-${item.id}`"
      :key="item.id"
      class="icon-button ui-tabs__tab"
      :class="{ 'ui-tabs__tab--active': item.id === modelValue }"
      type="button"
      role="tab"
      :aria-selected="item.id === modelValue"
      :aria-controls="`${idPrefix}-panel-${item.id}`"
      :tabindex="item.id === modelValue ? 0 : -1"
      @click="emit('update:modelValue', item.id)"
      @keydown="moveFocus($event, index)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.ui-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
.ui-tabs__tab {
  min-width: 108px;
}
.ui-tabs__tab--active {
  color: var(--accent-cool-strong);
  background:
    radial-gradient(circle at top left, rgba(255, 203, 148, 0.64), transparent 42%),
    linear-gradient(145deg, rgba(255, 247, 236, 0.98), rgba(231, 220, 205, 0.96));
  box-shadow: var(--elevation-2);
}
</style>
