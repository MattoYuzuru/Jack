<script setup lang="ts">
withDefaults(
  defineProps<{
    accent?: boolean
    compact?: boolean
  }>(),
  { accent: false, compact: false },
)
</script>

<template>
  <span class="chip-pill" :class="{ 'chip-pill--accent': accent, 'chip-pill--compact': compact }">
    <slot />
  </span>
</template>
