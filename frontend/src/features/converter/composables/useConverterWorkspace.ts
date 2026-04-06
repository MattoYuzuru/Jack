import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import {
  createConverterRuntime,
  type ConverterPreparedSource,
  type ConverterResult,
} from '../application/converter-runtime'
import {
  cancelProcessingJob,
  ProcessingJobCancelledError,
  type ProcessingJobResponse,
  type ProcessingJobStatus,
} from '../../processing/application/processing-client'
import {
  getConverterCapabilityMatrix,
  type ConverterScenarioDefinition,
  type ConverterTargetFormatDefinition,
} from '../domain/converter-registry'
import {
  resolveConverterPresetFromDefinitions,
  type ConverterPresetDefinition,
} from '../domain/converter-presets'

interface ConverterResultViewModel extends ConverterResult {
  id: string
  cacheKey: string
  sourceFileName: string
  objectUrl: string
  createdAt: string
}

interface ConverterRunRequest {
  prepared: ConverterPreparedSource
  targetExtension: string
  presetId: ConverterPresetDefinition['id']
  quality: number
  backgroundColor: string
  maxWidth: number | null
  maxHeight: number | null
  videoCodec: string
  audioCodec: string
  targetFps: number | null
  videoBitrateKbps: number | null
  audioBitrateKbps: number | null
  cacheKey: string
}

interface SelectOption {
  value: string
  label: string
}

const MAX_RESULT_HISTORY = 8
const converterRuntime = createConverterRuntime()
const MEDIA_SOURCE_EXTENSIONS = new Set([
  'mp4',
  'mov',
  'mkv',
  'avi',
  'webm',
  'wav',
  'flac',
  'mp3',
  'm4a',
])
const VIDEO_SOURCE_EXTENSIONS = new Set(['mp4', 'mov', 'mkv', 'avi', 'webm'])
const VIDEO_TARGET_EXTENSIONS = new Set(['mp4', 'webm', 'gif'])
const LOSSY_AUDIO_TARGET_EXTENSIONS = new Set(['mp3', 'aac', 'm4a'])
const RESOLUTION_OPTIONS: SelectOption[] = [
  { value: 'original', label: 'Original' },
  { value: '2160p', label: '2160p / 4K' },
  { value: '1440p', label: '1440p' },
  { value: '1080p', label: '1080p' },
  { value: '720p', label: '720p' },
  { value: '480p', label: '480p' },
]
const FPS_OPTIONS: SelectOption[] = [
  { value: '', label: 'Original FPS' },
  { value: '60', label: '60 fps' },
  { value: '30', label: '30 fps' },
  { value: '24', label: '24 fps' },
  { value: '15', label: '15 fps' },
  { value: '10', label: '10 fps' },
]
const VIDEO_BITRATE_OPTIONS: SelectOption[] = [
  { value: '', label: 'Auto bitrate' },
  { value: '8000', label: '8000 kbps' },
  { value: '5000', label: '5000 kbps' },
  { value: '2500', label: '2500 kbps' },
  { value: '1200', label: '1200 kbps' },
]
const AUDIO_BITRATE_OPTIONS: SelectOption[] = [
  { value: '', label: 'Auto bitrate' },
  { value: '320', label: '320 kbps' },
  { value: '192', label: '192 kbps' },
  { value: '128', label: '128 kbps' },
  { value: '96', label: '96 kbps' },
]
const VIDEO_CODEC_OPTIONS_BY_TARGET: Record<string, SelectOption[]> = {
  mp4: [
    { value: 'h264', label: 'H.264' },
    { value: 'av1', label: 'AV1' },
  ],
  webm: [
    { value: 'vp9', label: 'VP9' },
    { value: 'av1', label: 'AV1' },
  ],
}

function resolveMediaDefaults(presetId: ConverterPresetDefinition['id']) {
  switch (presetId) {
    case 'web-balanced':
      return {
        resolution: '1080p',
        fps: '30',
        videoBitrateKbps: '5000',
        audioBitrateKbps: '192',
      }
    case 'email-attachment':
      return {
        resolution: '720p',
        fps: '24',
        videoBitrateKbps: '2500',
        audioBitrateKbps: '128',
      }
    case 'thumbnail':
      return {
        resolution: '480p',
        fps: '10',
        videoBitrateKbps: '1200',
        audioBitrateKbps: '96',
      }
    default:
      return {
        resolution: 'original',
        fps: '',
        videoBitrateKbps: '',
        audioBitrateKbps: '',
      }
  }
}

function resolveDefaultVideoCodec(targetExtension: string): string {
  if (targetExtension === 'webm') {
    return 'vp9'
  }

  if (targetExtension === 'mp4') {
    return 'h264'
  }

  return ''
}

function resolveAudioCodecForTarget(targetExtension: string): string {
  switch (targetExtension) {
    case 'mp4':
    case 'm4a':
    case 'aac':
      return 'AAC'
    case 'webm':
      return 'Opus'
    case 'mp3':
      return 'MP3'
    case 'wav':
      return 'PCM 16-bit'
    case 'flac':
      return 'FLAC'
    default:
      return ''
  }
}

function resolveMediaSizing(resolutionId: string): { maxWidth: number | null; maxHeight: number | null } {
  switch (resolutionId) {
    case '2160p':
      return { maxWidth: 3840, maxHeight: 2160 }
    case '1440p':
      return { maxWidth: 2560, maxHeight: 1440 }
    case '1080p':
      return { maxWidth: 1920, maxHeight: 1080 }
    case '720p':
      return { maxWidth: 1280, maxHeight: 720 }
    case '480p':
      return { maxWidth: 854, maxHeight: 480 }
    default:
      return { maxWidth: null, maxHeight: null }
  }
}

function resolveMediaResolutionFromSizing(
  maxWidth: number | null,
  maxHeight: number | null,
): string {
  if (maxWidth === 3840 && maxHeight === 2160) {
    return '2160p'
  }
  if (maxWidth === 2560 && maxHeight === 1440) {
    return '1440p'
  }
  if (maxWidth === 1920 && maxHeight === 1080) {
    return '1080p'
  }
  if (maxWidth === 1280 && maxHeight === 720) {
    return '720p'
  }
  if (maxWidth === 854 && maxHeight === 480) {
    return '480p'
  }
  return 'original'
}

function parseNullableInteger(value: string): number | null {
  if (!value.trim()) {
    return null
  }

  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) ? parsed : null
}

export function useConverterWorkspace() {
  const prepared = shallowRef<ConverterPreparedSource | null>(null)
  const resultHistory = ref<ConverterResultViewModel[]>([])
  const selectedResultId = ref<string | null>(null)
  const isLoading = ref(false)
  const isConverting = ref(false)
  const isCancelling = ref(false)
  const errorMessage = ref('')
  const processingMessage = ref('')
  const converterAcceptAttribute = ref('')
  const availablePresets = ref<ConverterPresetDefinition[]>([])
  const imageScenarios = ref<ConverterScenarioDefinition[]>([])
  const documentScenarios = ref<ConverterScenarioDefinition[]>([])
  const mediaScenarios = ref<ConverterScenarioDefinition[]>([])
  const selectedTargetExtension = ref('')
  const selectedPresetId = ref<ConverterPresetDefinition['id']>('original')
  const quality = ref(0.9)
  const backgroundColor = ref('#fffaf0')
  const selectedVideoCodec = ref('h264')
  const selectedMediaResolution = ref('original')
  const selectedTargetFps = ref('')
  const selectedVideoBitrateKbps = ref('')
  const selectedAudioBitrateKbps = ref('')
  const activeJobId = ref('')
  const activeJobStatus = ref<ProcessingJobStatus | ''>('')
  const activeJobProgressPercent = ref(0)
  const lastRequest = shallowRef<ConverterRunRequest | null>(null)
  let capabilityMatrixRequest: Promise<void> | null = null
  let selectionRevision = 0
  let activeConversionToken = 0

  const availableTargets = computed(() => prepared.value?.targets ?? [])
  const activeTarget = computed<ConverterTargetFormatDefinition | null>(
    () =>
      availableTargets.value.find((target) => target.extension === selectedTargetExtension.value) ??
      null,
  )
  const activePreset = computed(() =>
    resolveConverterPresetFromDefinitions(availablePresets.value, selectedPresetId.value),
  )
  const hasMediaSource = computed(() =>
    Boolean(prepared.value && MEDIA_SOURCE_EXTENSIONS.has(prepared.value.source.extension)),
  )
  const hasVideoSource = computed(() =>
    Boolean(prepared.value && VIDEO_SOURCE_EXTENSIONS.has(prepared.value.source.extension)),
  )
  const showMediaControls = computed(() => hasMediaSource.value)
  const showVideoCodecControl = computed(
    () => hasVideoSource.value && Boolean(activeTarget.value && ['mp4', 'webm'].includes(activeTarget.value.extension)),
  )
  const showResolutionControl = computed(
    () => hasVideoSource.value && Boolean(activeTarget.value && VIDEO_TARGET_EXTENSIONS.has(activeTarget.value.extension)),
  )
  const showFpsControl = computed(
    () => hasVideoSource.value && Boolean(activeTarget.value && VIDEO_TARGET_EXTENSIONS.has(activeTarget.value.extension)),
  )
  const showVideoBitrateControl = computed(
    () => hasVideoSource.value && Boolean(activeTarget.value && ['mp4', 'webm'].includes(activeTarget.value.extension)),
  )
  const showAudioBitrateControl = computed(
    () =>
      Boolean(
        activeTarget.value &&
          (['mp4', 'webm'].includes(activeTarget.value.extension) ||
            LOSSY_AUDIO_TARGET_EXTENSIONS.has(activeTarget.value.extension)),
      ),
  )
  const availableVideoCodecOptions = computed<SelectOption[]>(
    () => (activeTarget.value ? VIDEO_CODEC_OPTIONS_BY_TARGET[activeTarget.value.extension] ?? [] : []),
  )
  const resolvedAudioCodec = computed(() => resolveAudioCodecForTarget(activeTarget.value?.extension ?? ''))
  const result = computed<ConverterResultViewModel | null>(
    () =>
      resultHistory.value.find((entry) => entry.id === selectedResultId.value) ??
      resultHistory.value[0] ??
      null,
  )
  const canRetry = computed(
    () => Boolean(lastRequest.value) && !isLoading.value && !isConverting.value,
  )
  const hasResultHistory = computed(() => resultHistory.value.length > 0)

  async function ensureCapabilityMatrix(): Promise<void> {
    if (!capabilityMatrixRequest) {
      capabilityMatrixRequest = loadCapabilityMatrix().catch((error) => {
        capabilityMatrixRequest = null
        throw error
      })
    }

    return capabilityMatrixRequest
  }

  async function loadCapabilityMatrix(): Promise<void> {
    const matrix = await getConverterCapabilityMatrix()
    converterAcceptAttribute.value = matrix.acceptAttribute
    availablePresets.value = (matrix.presets as ConverterPresetDefinition[]).filter(
      (preset) => preset.available,
    )
    imageScenarios.value = matrix.scenarios.filter(
      (scenario) => scenario.family === 'image' && scenario.available,
    )
    documentScenarios.value = matrix.scenarios.filter(
      (scenario) => scenario.family === 'document' && scenario.available,
    )
    mediaScenarios.value = matrix.scenarios.filter(
      (scenario) => scenario.family === 'media' && scenario.available,
    )
  }

  void ensureCapabilityMatrix().catch(() => undefined)

  watch([activeTarget, activePreset], ([target, preset]) => {
    if (!target || !preset) {
      return
    }

    quality.value = preset.preferredQuality ?? target.defaultQuality ?? quality.value
    backgroundColor.value = preset.defaultBackgroundColor ?? backgroundColor.value

    if (hasMediaSource.value) {
      const defaults = resolveMediaDefaults(preset.id)
      selectedVideoCodec.value = resolveDefaultVideoCodec(target.extension)
      selectedMediaResolution.value = hasVideoSource.value && VIDEO_TARGET_EXTENSIONS.has(target.extension)
        ? defaults.resolution
        : 'original'
      selectedTargetFps.value = hasVideoSource.value && VIDEO_TARGET_EXTENSIONS.has(target.extension)
        ? defaults.fps
        : ''
      selectedVideoBitrateKbps.value = hasVideoSource.value && ['mp4', 'webm'].includes(target.extension)
        ? defaults.videoBitrateKbps
        : ''
      selectedAudioBitrateKbps.value =
        ['mp4', 'webm'].includes(target.extension) || LOSSY_AUDIO_TARGET_EXTENSIONS.has(target.extension)
          ? defaults.audioBitrateKbps
          : ''
    }
  })

  function buildCacheKey(request: {
    prepared: ConverterPreparedSource
    targetExtension: string
    presetId: string
    quality: number
    backgroundColor: string
    maxWidth: number | null
    maxHeight: number | null
    videoCodec: string
    audioCodec: string
    targetFps: number | null
    videoBitrateKbps: number | null
    audioBitrateKbps: number | null
  }): string {
    const source = request.prepared.file
    return [
      source.name,
      source.type || 'application/octet-stream',
      source.size,
      source.lastModified,
      request.prepared.source.extension,
      request.targetExtension,
      request.presetId,
      request.quality.toFixed(4),
      request.backgroundColor.trim().toLowerCase(),
      request.maxWidth ?? 'auto-width',
      request.maxHeight ?? 'auto-height',
      request.videoCodec || 'auto-video-codec',
      request.audioCodec || 'auto-audio-codec',
      request.targetFps ?? 'auto-fps',
      request.videoBitrateKbps ?? 'auto-video-bitrate',
      request.audioBitrateKbps ?? 'auto-audio-bitrate',
    ].join('::')
  }

  function rememberResult(entry: ConverterResultViewModel) {
    const existingIndex = resultHistory.value.findIndex(
      (candidate) => candidate.cacheKey === entry.cacheKey,
    )

    if (existingIndex !== -1) {
      releaseHistoryEntry(resultHistory.value[existingIndex] ?? null)
      resultHistory.value.splice(existingIndex, 1)
    }

    resultHistory.value.unshift(entry)
    selectedResultId.value = entry.id

    while (resultHistory.value.length > MAX_RESULT_HISTORY) {
      releaseHistoryEntry(resultHistory.value.pop() ?? null)
    }
  }

  function releaseHistoryEntry(entry: ConverterResultViewModel | null) {
    if (!entry) {
      return
    }

    URL.revokeObjectURL(entry.objectUrl)
  }

  function selectResult(entryId: string) {
    const existingIndex = resultHistory.value.findIndex((entry) => entry.id === entryId)
    if (existingIndex === -1) {
      return
    }

    const existingEntry = resultHistory.value[existingIndex]
    if (!existingEntry) {
      return
    }

    resultHistory.value.splice(existingIndex, 1)
    resultHistory.value.unshift(existingEntry)
    selectedResultId.value = existingEntry.id
    processingMessage.value =
      'Переиспользую уже собранный backend artifact из текущей сессии без повторного processing job.'
  }

  function registerJobSnapshot(job: ProcessingJobResponse) {
    activeJobId.value = job.id
    activeJobStatus.value = job.status
    activeJobProgressPercent.value = job.progressPercent

    if (job.message) {
      processingMessage.value = job.message
    }
  }

  // Если пользователь быстро меняет source или повторно запускает convert,
  // поздний ответ старого job не должен перезаписать уже актуальный workspace state.
  function isCurrentConversion(token: number, revision: number): boolean {
    return token === activeConversionToken && revision === selectionRevision
  }

  function buildRunRequest(): ConverterRunRequest | null {
    if (!prepared.value || !selectedTargetExtension.value) {
      return null
    }

    const mediaSizing = resolveMediaSizing(selectedMediaResolution.value)
    const audioCodec = resolvedAudioCodec.value

    return {
      prepared: prepared.value,
      targetExtension: selectedTargetExtension.value,
      presetId: selectedPresetId.value,
      quality: quality.value,
      backgroundColor: backgroundColor.value,
      maxWidth: hasMediaSource.value ? mediaSizing.maxWidth : null,
      maxHeight: hasMediaSource.value ? mediaSizing.maxHeight : null,
      videoCodec: hasMediaSource.value ? selectedVideoCodec.value : '',
      audioCodec,
      targetFps: hasMediaSource.value ? parseNullableInteger(selectedTargetFps.value) : null,
      videoBitrateKbps: hasMediaSource.value
        ? parseNullableInteger(selectedVideoBitrateKbps.value)
        : null,
      audioBitrateKbps: hasMediaSource.value
        ? parseNullableInteger(selectedAudioBitrateKbps.value)
        : null,
      cacheKey: buildCacheKey({
        prepared: prepared.value,
        targetExtension: selectedTargetExtension.value,
        presetId: selectedPresetId.value,
        quality: quality.value,
        backgroundColor: backgroundColor.value,
        maxWidth: hasMediaSource.value ? mediaSizing.maxWidth : null,
        maxHeight: hasMediaSource.value ? mediaSizing.maxHeight : null,
        videoCodec: hasMediaSource.value ? selectedVideoCodec.value : '',
        audioCodec,
        targetFps: hasMediaSource.value ? parseNullableInteger(selectedTargetFps.value) : null,
        videoBitrateKbps: hasMediaSource.value
          ? parseNullableInteger(selectedVideoBitrateKbps.value)
          : null,
        audioBitrateKbps: hasMediaSource.value
          ? parseNullableInteger(selectedAudioBitrateKbps.value)
          : null,
      }),
    }
  }

  function createHistoryEntry(
    converted: ConverterResult,
    request: ConverterRunRequest,
  ): ConverterResultViewModel {
    return {
      ...converted,
      id: crypto.randomUUID(),
      cacheKey: request.cacheKey,
      sourceFileName: request.prepared.file.name,
      objectUrl: URL.createObjectURL(converted.previewBlob),
      createdAt: converted.backendCompletedAt ?? new Date().toISOString(),
    }
  }

  async function selectFile(file: File) {
    const revision = ++selectionRevision
    lastRequest.value = null
    prepared.value = null
    errorMessage.value = ''
    processingMessage.value = 'Подготавливаю source-сценарии для выбранного файла...'
    isLoading.value = true

    if (isConverting.value) {
      await requestCancellation({ silent: true })
    }

    try {
      await ensureCapabilityMatrix()
      const inspected = await converterRuntime.inspect(file)

      if (revision !== selectionRevision) {
        return
      }

      if (!inspected || !inspected.targets.length) {
        throw new Error('Для выбранного файла пока нет зарегистрированного сценария конвертации.')
      }

      prepared.value = inspected
      selectedTargetExtension.value = inspected.targets[0]?.extension ?? ''
      selectedPresetId.value = 'original'
    } catch (error) {
      if (revision !== selectionRevision) {
        return
      }

      errorMessage.value =
        error instanceof Error
          ? error.message
          : 'Не удалось подготовить конвертер к выбранному файлу.'
    } finally {
      if (revision === selectionRevision) {
        isLoading.value = false
        if (!prepared.value && !result.value) {
          processingMessage.value = ''
        }
      }
    }
  }

  function clearSelection() {
    selectionRevision += 1
    if (isConverting.value) {
      void requestCancellation({ silent: true })
    }

    prepared.value = null
    errorMessage.value = ''
    isLoading.value = false
    selectedTargetExtension.value = ''
    selectedPresetId.value = 'original'
    selectedVideoCodec.value = 'h264'
    selectedMediaResolution.value = 'original'
    selectedTargetFps.value = ''
    selectedVideoBitrateKbps.value = ''
    selectedAudioBitrateKbps.value = ''
    lastRequest.value = null
    processingMessage.value = result.value
      ? 'Последний backend result сохранён в session history и доступен для повторного скачивания.'
      : ''
  }

  async function runConversion(request: ConverterRunRequest) {
    const cachedResult = resultHistory.value.find((entry) => entry.cacheKey === request.cacheKey)
    if (cachedResult) {
      selectResult(cachedResult.id)
      errorMessage.value = ''
      return
    }

    const conversionToken = ++activeConversionToken
    const revision = selectionRevision
    errorMessage.value = ''
    processingMessage.value = 'Создаю backend-first conversion job через processing platform...'
    isConverting.value = true
    isCancelling.value = false
    activeJobProgressPercent.value = 0
    activeJobStatus.value = 'QUEUED'

    try {
      const converted = await converterRuntime.convert({
        prepared: request.prepared,
        targetExtension: request.targetExtension,
        presetId: request.presetId,
        quality: request.quality,
        backgroundColor: request.backgroundColor,
        maxWidth: request.maxWidth,
        maxHeight: request.maxHeight,
        videoCodec: request.videoCodec,
        audioCodec: request.audioCodec,
        targetFps: request.targetFps,
        videoBitrateKbps: request.videoBitrateKbps,
        audioBitrateKbps: request.audioBitrateKbps,
        onProgress(message) {
          if (isCurrentConversion(conversionToken, revision)) {
            processingMessage.value = message
          }
        },
        onJobCreated(jobId) {
          if (!isCurrentConversion(conversionToken, revision)) {
            return
          }

          activeJobId.value = jobId
          activeJobStatus.value = 'QUEUED'
        },
        onJobUpdate(job) {
          if (!isCurrentConversion(conversionToken, revision)) {
            return
          }

          registerJobSnapshot(job as ProcessingJobResponse)
        },
      })

      if (!isCurrentConversion(conversionToken, revision)) {
        return
      }

      const entry = createHistoryEntry(converted, request)
      rememberResult(entry)
      processingMessage.value =
        'Backend conversion завершён: result artifact сохранён в history и готов к повторному скачиванию.'
    } catch (error) {
      if (!isCurrentConversion(conversionToken, revision)) {
        return
      }

      if (error instanceof ProcessingJobCancelledError) {
        registerJobSnapshot(error.job)
        errorMessage.value = ''
        processingMessage.value =
          'Конвертация остановлена. Можно повторить запрос с теми же параметрами без повторного выбора файла.'
        return
      }

      errorMessage.value =
        error instanceof Error ? error.message : 'Не удалось выполнить конвертацию.'
    } finally {
      if (isCurrentConversion(conversionToken, revision)) {
        isConverting.value = false
        isCancelling.value = false
      }
    }
  }

  async function convert() {
    const request = buildRunRequest()
    if (!request) {
      return
    }

    lastRequest.value = request
    await runConversion(request)
  }

  async function retryLastConversion() {
    if (!lastRequest.value) {
      return
    }

    prepared.value = lastRequest.value.prepared
    selectedTargetExtension.value = lastRequest.value.targetExtension
    selectedPresetId.value = lastRequest.value.presetId
    quality.value = lastRequest.value.quality
    backgroundColor.value = lastRequest.value.backgroundColor
    selectedVideoCodec.value = lastRequest.value.videoCodec
    selectedMediaResolution.value = resolveMediaResolutionFromSizing(
      lastRequest.value.maxWidth,
      lastRequest.value.maxHeight,
    )
    selectedTargetFps.value = lastRequest.value.targetFps ? String(lastRequest.value.targetFps) : ''
    selectedVideoBitrateKbps.value = lastRequest.value.videoBitrateKbps
      ? String(lastRequest.value.videoBitrateKbps)
      : ''
    selectedAudioBitrateKbps.value = lastRequest.value.audioBitrateKbps
      ? String(lastRequest.value.audioBitrateKbps)
      : ''

    await runConversion(lastRequest.value)
  }

  async function requestCancellation(options: { silent?: boolean } = {}) {
    if (!activeJobId.value || !isConverting.value) {
      return
    }

    isCancelling.value = true
    if (!options.silent) {
      processingMessage.value = 'Отправляю запрос на отмену активного backend processing job...'
    }

    try {
      const cancelledJob = await cancelProcessingJob(activeJobId.value)
      registerJobSnapshot(cancelledJob)
    } catch (error) {
      if (!options.silent) {
        errorMessage.value =
          error instanceof Error ? error.message : 'Не удалось отменить текущую конвертацию.'
        isCancelling.value = false
      }
    } finally {
      if (options.silent) {
        isCancelling.value = false
      }
    }
  }

  async function cancelConversion() {
    await requestCancellation()
  }

  function downloadEntry(entry: ConverterResultViewModel | null) {
    if (!entry) {
      return
    }

    const downloadUrl = URL.createObjectURL(entry.blob)
    const anchor = document.createElement('a')
    anchor.href = downloadUrl
    anchor.download = entry.fileName
    anchor.click()
    URL.revokeObjectURL(downloadUrl)
  }

  function downloadResult() {
    downloadEntry(result.value)
  }

  function downloadHistoryEntry(entryId: string) {
    const entry = resultHistory.value.find((candidate) => candidate.id === entryId) ?? null
    downloadEntry(entry)
  }

  onBeforeUnmount(() => {
    for (const entry of resultHistory.value) {
      releaseHistoryEntry(entry)
    }
  })

  return {
    prepared,
    result,
    resultHistory,
    availablePresets,
    availableTargets,
    activeTarget,
    activePreset,
    isLoading,
    isConverting,
    isCancelling,
    canRetry,
    hasResultHistory,
    errorMessage,
    processingMessage,
    selectedTargetExtension,
    selectedPresetId,
    quality,
    backgroundColor,
    selectedVideoCodec,
    selectedMediaResolution,
    selectedTargetFps,
    selectedVideoBitrateKbps,
    selectedAudioBitrateKbps,
    showMediaControls,
    showVideoCodecControl,
    showResolutionControl,
    showFpsControl,
    showVideoBitrateControl,
    showAudioBitrateControl,
    availableVideoCodecOptions,
    resolvedAudioCodec,
    resolutionOptions: RESOLUTION_OPTIONS,
    fpsOptions: FPS_OPTIONS,
    videoBitrateOptions: VIDEO_BITRATE_OPTIONS,
    audioBitrateOptions: AUDIO_BITRATE_OPTIONS,
    converterAcceptAttribute,
    imageScenarios,
    documentScenarios,
    mediaScenarios,
    activeJobId,
    activeJobStatus,
    activeJobProgressPercent,
    selectFile,
    clearSelection,
    convert,
    retryLastConversion,
    cancelConversion,
    selectResult,
    downloadResult,
    downloadHistoryEntry,
  }
}
