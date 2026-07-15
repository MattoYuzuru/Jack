<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit' | 'reset'
    variant?: 'default' | 'accent' | 'icon'
  }>(),
  { type: 'button', variant: 'default' },
)
</script>

<template>
  <button
    :type="type"
    :class="[
      variant === 'icon' ? 'icon-button' : 'action-button',
      { 'action-button--accent': variant === 'accent' },
    ]"
  >
    <slot />
  </button>
</template>
