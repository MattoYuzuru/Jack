<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { ViewerResolvedVideo } from '../../application/viewer-runtime'
import type { ViewerVideoSubtitleTrack } from '../../composables/useViewerVideoPlayback'

defineProps<{
  selection: ViewerResolvedVideo
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
  canUsePictureInPicture: boolean
  isPictureInPictureActive: boolean
  isLooping: boolean
  assumedFrameRate: number
  frameRateOptions: number[]
  frameStepLabel: string
  approximateFrameNumber: number
  subtitleTracks: ViewerVideoSubtitleTrack[]
  activeSubtitleTrack: ViewerVideoSubtitleTrack | null
  activeSubtitleTrackId: 'off' | number
  playbackMessage: string
  subtitleMessage: string
  posterMessage: string
  posterCount: number
}>()

const emit = defineEmits<{
  elementChange: [element: HTMLVideoElement | null]
  togglePlayback: []
  stepFrame: [direction: -1 | 1]
  seekBy: [seconds: number]
  seekTo: [seconds: number]
  setVolume: [volume: number]
  toggleMute: []
  setPlaybackRate: [rate: number]
  setFrameRate: [rate: number]
  toggleLoop: []
  openSubtitles: []
  capturePoster: []
  clearSubtitles: []
  togglePictureInPicture: []
  copyTimestamp: []
}>()

const videoElement = ref<HTMLVideoElement | null>(null)
onMounted(() => emit('elementChange', videoElement.value))
onBeforeUnmount(() => emit('elementChange', null))

function inputNumber(event: Event): number {
  return Number((event.target as HTMLInputElement | HTMLSelectElement).value)
}
</script>

<template>
  <div class="viewer-video-frame" data-testid="viewer-video-renderer">
    <div class="video-stage-hud">
      <div class="document-stage-hud__meta">
        <span class="chip-pill chip-pill--compact chip-pill--accent">{{
          selection.format.label
        }}</span>
        <span v-for="metric in metrics" :key="metric" class="chip-pill chip-pill--compact">{{
          metric
        }}</span>
      </div>
      <div class="document-stage-hud__actions">
        <button class="action-button" type="button" @click="emit('togglePlayback')">
          {{ isPlaying ? 'Пауза' : 'Воспроизвести' }}
        </button>
        <button class="action-button" type="button" @click="emit('stepFrame', -1)">-1f</button>
        <button class="action-button" type="button" @click="emit('stepFrame', 1)">+1f</button>
        <button class="action-button" type="button" @click="emit('seekBy', -5)">-5s</button>
        <button class="action-button" type="button" @click="emit('seekBy', 5)">+5s</button>
        <button class="action-button" type="button" @click="emit('toggleLoop')">
          {{ isLooping ? 'Повтор включён' : 'Повтор выключен' }}
        </button>
        <button class="action-button" type="button" @click="emit('openSubtitles')">Субтитры</button>
        <button class="action-button" type="button" @click="emit('capturePoster')">Кадр</button>
        <button
          class="action-button"
          type="button"
          :disabled="!canUsePictureInPicture"
          @click="emit('togglePictureInPicture')"
        >
          {{ isPictureInPictureActive ? 'Выйти из PiP' : 'PiP' }}
        </button>
      </div>
    </div>

    <video
      ref="videoElement"
      class="viewer-video-frame__player"
      :src="selection.layout.objectUrl"
      :loop="isLooping"
      preload="metadata"
      playsinline
    >
      <track
        v-for="track in subtitleTracks"
        :key="track.id"
        :kind="track.kind"
        :label="track.label"
        :srclang="track.language"
        :src="track.objectUrl"
        :default="track.id === activeSubtitleTrackId"
      />
    </video>

    <div class="video-control-panel">
      <label class="video-progress">
        <span>{{ currentTimeLabel }}</span>
        <input
          type="range"
          min="0"
          :max="durationSeconds || selection.layout.durationSeconds || 0"
          step="0.1"
          :value="currentTime"
          @input="emit('seekTo', inputNumber($event))"
        />
        <span>{{ durationLabel }}</span>
      </label>
      <p v-if="playbackMessage" class="video-tool-message" role="status">
        {{ playbackMessage }}
      </p>
      <p v-if="subtitleMessage" class="video-tool-message" role="status">
        {{ subtitleMessage }}
      </p>
      <p v-if="posterMessage" class="video-tool-message" role="status">
        {{ posterMessage }}
      </p>
      <div class="video-control-row">
        <label class="video-slider"
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
        <label class="video-rate"
          ><span>Скорость</span
          ><select :value="playbackRate" @change="emit('setPlaybackRate', inputNumber($event))">
            <option v-for="rate in playbackRates" :key="rate" :value="rate">{{ rate }}x</option>
          </select></label
        >
        <label class="video-rate"
          ><span>FPS</span
          ><select :value="assumedFrameRate" @change="emit('setFrameRate', inputNumber($event))">
            <option v-for="rate in frameRateOptions" :key="rate" :value="rate">
              {{ rate }} fps
            </option>
          </select></label
        >
        <span class="chip-pill chip-pill--compact">{{ progressPercent.toFixed(0) }}%</span>
        <span class="chip-pill chip-pill--compact">{{ frameStepLabel }} / frame</span>
        <span class="chip-pill chip-pill--compact">{{
          approximateFrameNumber ? `Кадр #${approximateFrameNumber}` : 'Кадр —'
        }}</span>
      </div>
      <div class="video-control-row">
        <div class="video-actions">
          <button class="action-button" type="button" @click="emit('copyTimestamp')">
            Скопировать время
          </button>
          <button class="action-button" type="button" @click="emit('capturePoster')">
            Сохранить кадр
          </button>
          <button class="action-button" type="button" @click="emit('openSubtitles')">
            Добавить субтитры
          </button>
          <button
            class="action-button"
            type="button"
            :disabled="!subtitleTracks.length"
            @click="emit('clearSubtitles')"
          >
            Очистить
          </button>
        </div>
        <div class="document-stage-hud__meta">
          <span class="chip-pill chip-pill--compact">{{
            isLooping ? 'Повтор включён' : 'Один проход'
          }}</span>
          <span class="chip-pill chip-pill--compact">{{
            activeSubtitleTrack ? `Активно: ${activeSubtitleTrack.label}` : 'Субтитры выключены'
          }}</span>
          <span class="chip-pill chip-pill--compact chip-pill--accent">{{
            posterCount ? `${posterCount} кадров` : 'Кадры ещё не сохранены'
          }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.viewer-video-frame {
  display: grid;
  width: 100%;
  min-height: 480px;
  place-items: start center;
  gap: 16px;
  overflow: auto;
}
.viewer-video-frame__player {
  width: min(100%, 980px);
  max-height: 68vh;
  border-radius: 24px;
  background: rgba(17, 27, 28, 0.94);
  box-shadow: 0 22px 46px rgba(20, 48, 45, 0.24);
}
.video-stage-hud,
.document-stage-hud__meta,
.document-stage-hud__actions,
.video-control-row,
.video-progress,
.video-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.video-stage-hud {
  width: 100%;
  align-items: flex-start;
  justify-content: space-between;
}
.document-stage-hud__meta {
  align-items: center;
}
.document-stage-hud__actions {
  justify-content: flex-end;
}
.video-control-panel {
  display: grid;
  gap: 12px;
  width: min(100%, 980px);
  padding: 18px;
  border-radius: var(--radius-xl);
  background: rgba(255, 250, 242, 0.9);
  box-shadow: var(--shadow-pressed);
}
.video-tool-message {
  width: 100%;
  margin: 0;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 246, 232, 0.9);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}
.video-progress,
.video-slider,
.video-rate {
  align-items: center;
}
.video-progress {
  width: 100%;
}
.video-progress input,
.video-slider input {
  width: 100%;
}
.video-control-row {
  align-items: center;
  justify-content: space-between;
}
.video-slider,
.video-rate {
  display: flex;
  gap: 10px;
  color: var(--text-main);
}
.video-rate select {
  min-height: 44px;
  padding: 8px 12px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 250, 242, 0.84);
  box-shadow: var(--shadow-pressed);
  color: var(--text-main);
}
</style>
