<script setup lang="ts">
withDefaults(
  defineProps<{
    as?: 'article' | 'section' | 'aside' | 'div'
    elevation?: 'raised' | 'floating' | 'inset' | 'flat'
  }>(),
  { as: 'article', elevation: 'raised' },
)
</script>

<template>
  <component :is="as" class="panel-surface ui-panel" :class="`ui-panel--${elevation}`">
    <slot />
  </component>
</template>

<style scoped>
.ui-panel {
  min-width: 0;
}
.ui-panel--flat {
  box-shadow: var(--elevation-0);
}
.ui-panel--inset {
  box-shadow: var(--elevation-inset);
}
.ui-panel--floating {
  box-shadow: var(--elevation-floating);
}
</style>
