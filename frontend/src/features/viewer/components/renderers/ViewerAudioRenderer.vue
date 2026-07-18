<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { ViewerResolvedAudio } from '../../application/viewer-runtime'

defineProps<{
  selection: ViewerResolvedAudio
  metrics: string[]
  isPlaying: boolean
  isMuted: boolean
  volume: number
  playbackRate: number
  playbackRates: number[]
  currentTime: number
  durationSeconds: number
  progressPercent: number
  currentTimeLabel: string
  durationLabel: string
  isLooping: boolean
  playbackMessage: string
}>()

const emit = defineEmits<{
  elementChange: [element: HTMLAudioElement | null]
  togglePlayback: []
  seekBy: [seconds: number]
  seekTo: [seconds: number]
  setVolume: [volume: number]
  toggleMute: []
  setPlaybackRate: [rate: number]
  toggleLoop: []
  copyTimestamp: []
}>()

const audioElement = ref<HTMLAudioElement | null>(null)
onMounted(() => emit('elementChange', audioElement.value))
onBeforeUnmount(() => emit('elementChange', null))
function inputNumber(event: Event): number {
  return Number((event.target as HTMLInputElement | HTMLSelectElement).value)
}
</script>

<template>
  <div class="viewer-audio-frame" data-testid="viewer-audio-renderer">
    <div class="audio-stage-hud">
      <div class="audio-stage-meta">
        <span class="chip-pill chip-pill--compact chip-pill--accent">{{
          selection.format.label
        }}</span>
        <span v-for="metric in metrics" :key="metric" class="chip-pill chip-pill--compact">{{
          metric
        }}</span>
      </div>
      <div class="audio-stage-actions">
        <button class="action-button" type="button" @click="emit('togglePlayback')">
          {{ isPlaying ? 'Пауза' : 'Воспроизвести' }}
        </button>
        <button class="action-button" type="button" @click="emit('seekBy', -10)">-10s</button>
        <button class="action-button" type="button" @click="emit('seekBy', 10)">+10s</button>
        <button class="action-button" type="button" @click="emit('toggleLoop')">
          {{ isLooping ? 'Повтор включён' : 'Повтор выключен' }}
        </button>
        <button class="action-button" type="button" @click="emit('copyTimestamp')">
          Скопировать время
        </button>
      </div>
    </div>
    <div class="audio-stage-shell">
      <div class="audio-stage-summary">
        <div v-if="selection.artworkDataUrl" class="audio-stage-artwork">
          <img :src="selection.artworkDataUrl" :alt="`${selection.file.name} artwork`" />
        </div>
        <div v-else class="audio-stage-artwork audio-stage-artwork--empty">
          <strong>{{ selection.format.label }}</strong
          ><span>Обложка не найдена</span>
        </div>
        <div class="audio-stage-copy">
          <p class="eyebrow">Аудио</p>
          <h3>{{ selection.file.name }}</h3>
          <p>Прослушивание, волна, теги и обложка собраны в одном окне.</p>
        </div>
      </div>
      <div class="audio-waveform" aria-label="Волна аудио">
        <div
          v-for="(bucket, index) in selection.layout.waveform"
          :key="index"
          class="audio-waveform__bar"
          :style="{ height: `${Math.max(bucket * 100, 8)}%` }"
        ></div>
      </div>
      <audio
        ref="audioElement"
        class="viewer-audio-frame__player"
        :src="selection.layout.objectUrl"
        :loop="isLooping"
        preload="metadata"
      ></audio>
      <div class="audio-control-panel">
        <label class="audio-progress"
          ><span>{{ currentTimeLabel }}</span
          ><input
            type="range"
            min="0"
            :max="durationSeconds || selection.layout.durationSeconds || 0"
            step="0.1"
            :value="currentTime"
            @input="emit('seekTo', inputNumber($event))"
          /><span>{{ durationLabel }}</span></label
        >
        <p v-if="playbackMessage" class="audio-tool-message" role="status">
          {{ playbackMessage }}
        </p>
        <div class="audio-control-row">
          <label class="audio-slider"
            ><span>Громкость</span
            ><input
              type="range"
              min="0"
              max="1"
              step="0.01"
              :value="volume"
              @input="emit('setVolume', inputNumber($event))"
          /></label>
          <button class="action-button" type="button" @click="emit('toggleMute')">
            {{ isMuted ? 'Со звуком' : 'Без звука' }}
          </button>
          <label class="audio-rate"
            ><span>Скорость</span
            ><select :value="playbackRate" @change="emit('setPlaybackRate', inputNumber($event))">
              <option v-for="rate in playbackRates" :key="rate" :value="rate">{{ rate }}x</option>
            </select></label
          >
          <span class="chip-pill chip-pill--compact">{{ progressPercent.toFixed(0) }}%</span>
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            selection.layout.waveform.length ? 'Волна готова' : 'Волна недоступна'
          }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.viewer-audio-frame {
  display: grid;
  width: 100%;
  min-height: 480px;
  place-items: start center;
  gap: 16px;
  overflow: auto;
}
.audio-stage-hud,
.audio-stage-meta,
.audio-stage-actions,
.audio-control-row,
.audio-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.audio-stage-hud {
  width: 100%;
  align-items: flex-start;
  justify-content: space-between;
}
.audio-stage-shell {
  display: grid;
  gap: 16px;
  width: min(100%, 980px);
  padding: 22px;
  border-radius: 28px;
  background: rgba(255, 251, 245, 0.94);
  box-shadow: var(--shadow-pressed);
}
.audio-stage-summary {
  display: grid;
  grid-template-columns: minmax(180px, 220px) minmax(0, 1fr);
  align-items: center;
  gap: 16px;
}
.audio-stage-artwork {
  display: grid;
  place-items: center;
  min-height: 180px;
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  overflow: hidden;
}
.audio-stage-artwork img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 18px;
}
.audio-stage-artwork--empty {
  text-align: center;
  color: var(--text-soft);
}
.audio-stage-copy {
  display: grid;
  gap: 12px;
}
.audio-stage-copy h3,
.audio-stage-copy p {
  margin: 0;
}
.audio-waveform {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8px, 1fr));
  align-items: end;
  gap: 6px;
  min-height: 140px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 250, 242, 0.88);
  box-shadow: var(--shadow-pressed);
}
.audio-waveform__bar {
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 157, 97, 0.98), rgba(29, 92, 85, 0.92));
}
.viewer-audio-frame__player {
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.audio-control-panel {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 242, 0.9);
  box-shadow: var(--shadow-pressed);
}
.audio-progress {
  width: 100%;
  align-items: center;
}
.audio-progress input,
.audio-slider input {
  width: 100%;
}
.audio-control-row {
  align-items: center;
  justify-content: space-between;
}
.audio-slider,
.audio-rate {
  display: flex;
  gap: 10px;
  align-items: center;
}
.audio-rate select {
  min-height: 44px;
  padding: 8px 12px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
}
.audio-tool-message {
  margin: 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 246, 232, 0.9);
  box-shadow: var(--shadow-pressed);
}
@media (max-width: 700px) {
  .audio-stage-summary {
    grid-template-columns: 1fr;
  }
}
</style>
