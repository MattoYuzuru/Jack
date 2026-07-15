<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
}>()
</script>

<template>
  <header class="panel-surface app-topbar workspace-header">
    <div class="brand-lockup">
      <img class="brand-lockup__logo" src="/logo.svg" alt="Логотип Jack" />
      <div class="brand-lockup__copy">
        <p class="eyebrow">{{ eyebrow }}</p>
        <p class="brand-lockup__title">{{ title }}</p>
      </div>
    </div>
    <div class="workspace-header__actions"><slot name="actions" /></div>
  </header>
</template>

<style scoped>
.workspace-header__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-3);
  min-width: 0;
}

@media (max-width: 860px) {
  .workspace-header__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .workspace-header__actions {
    display: grid;
    grid-template-columns: 1fr;
    width: 100%;
  }

  .workspace-header__actions :deep(> *) {
    width: 100%;
  }
}
</style>
