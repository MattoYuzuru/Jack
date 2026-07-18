<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { ViewerResolvedImage } from '../../application/viewer-runtime'

defineProps<{
  selection: ViewerResolvedImage
  viewportTransform: string
}>()

const emit = defineEmits<{
  elementChange: [element: HTMLImageElement | null]
  pointerMove: [event: PointerEvent]
  pointerLeave: [event: PointerEvent]
  storeSwatch: [event: MouseEvent]
}>()

const imageElement = ref<HTMLImageElement | null>(null)

onMounted(() => emit('elementChange', imageElement.value))
onBeforeUnmount(() => emit('elementChange', null))
</script>

<template>
  <div class="viewer-image-frame" data-testid="viewer-image-renderer">
    <img
      ref="imageElement"
      class="viewer-image-frame__image"
      :src="selection.objectUrl"
      :alt="selection.file.name"
      :style="{ transform: viewportTransform }"
      @pointermove="emit('pointerMove', $event)"
      @pointerleave="emit('pointerLeave', $event)"
      @click="emit('storeSwatch', $event)"
    />
  </div>
</template>

<style scoped>
.viewer-image-frame {
  display: grid;
  width: 100%;
  min-height: 480px;
  place-items: center;
  overflow: auto;
}

.viewer-image-frame__image {
  max-width: min(100%, 920px);
  max-height: 72vh;
  object-fit: contain;
  border-radius: 24px;
  box-shadow:
    0 22px 46px rgba(20, 48, 45, 0.18),
    0 2px 0 rgba(255, 255, 255, 0.6);
  transform-origin: center center;
  transition: transform 180ms ease;
}
</style>
