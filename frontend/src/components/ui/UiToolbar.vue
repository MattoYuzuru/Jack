<script setup lang="ts">
defineProps<{ label: string }>()
</script>

<template>
  <div class="ui-toolbar" role="toolbar" :aria-label="label"><slot /></div>
</template>

<style scoped>
.ui-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}
</style>
