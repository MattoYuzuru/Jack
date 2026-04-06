import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import {
  runServerCompression,
  type CompressionAttempt,
  type CompressionFact,
  type ServerCompressionManifest,
} from '../application/compression-server-runtime'
import {
  cancelProcessingJob,
  ProcessingJobCancelledError,
  type ProcessingJobStatus,
} from '../../processing/application/processing-client'
import {
  getCompressionAcceptAttribute,
  getCompressionCapabilityMatrix,
  getCompressionModes,
  listCompressionTargetsForSource,
  resolveCompressionSourceFormat,
  type CompressionFormatFamily,
  type CompressionModeDefinition,
  type CompressionSourceFormatDefinition,
  type CompressionTargetFormatDefinition,
} from '../domain/compression-registry'

interface CompressionPreparedSource {
  file: File
  source: CompressionSourceFormatDefinition
  targets: CompressionTargetFormatDefinition[]
}

interface CompressionRunRequest {
  file: File
  mode: CompressionModeDefinition['id']
  targetExtension: string | null
  targetSizeBytes: number | null
  maxWidth: number | null
  maxHeight: number | null
  quality: number | null
  backgroundColor: string
  targetFps: number | null
  videoBitrateKbps: number | null
  audioBitrateKbps: number | null
  presetLabel: string
}

interface CompressionResultViewModel {
  id: string
  sourceFileName: string
  fileName: string
  family: CompressionFormatFamily
  mode: ServerCompressionManifest['mode']
  targetMet: boolean
  sourceSizeBytes: number
  resultSizeBytes: number
  reductionPercent: number
  warnings: string[]
  sourceFacts: CompressionFact[]
  resultFacts: CompressionFact[]
  compressionFacts: CompressionFact[]
  attempts: CompressionAttempt[]
  backendJobId: string
  backendRuntimeLabel: string
  createdAt: string
  resultObjectUrl: string
  previewObjectUrl: string
  previewKind: ServerCompressionManifest['previewKind']
  previewMimeType: string
  resultMimeType: string
}

interface SelectOption {
  value: string
  label: string
}

type SizeUnit = 'KB' | 'MB'

const MAX_RESULT_HISTORY = 6
const RESOLUTION_OPTIONS: SelectOption[] = [
  { value: 'original', label: 'Исходный размер' },
  { value: '2160p', label: '2160p / 4K' },
  { value: '1440p', label: '1440p' },
  { value: '1080p', label: '1080p' },
  { value: '720p', label: '720p' },
  { value: '480p', label: '480p' },
]
const FPS_OPTIONS: SelectOption[] = [
  { value: '', label: 'Исходная частота' },
  { value: '60', label: '60 fps' },
  { value: '30', label: '30 fps' },
  { value: '24', label: '24 fps' },
  { value: '15', label: '15 fps' },
  { value: '12', label: '12 fps' },
]
const VIDEO_BITRATE_OPTIONS: SelectOption[] = [
  { value: '', label: 'Авто' },
  { value: '5000', label: '5000 kbps' },
  { value: '2500', label: '2500 kbps' },
  { value: '1200', label: '1200 kbps' },
  { value: '800', label: '800 kbps' },
]
const AUDIO_BITRATE_OPTIONS: SelectOption[] = [
  { value: '', label: 'Авто' },
  { value: '192', label: '192 kbps' },
  { value: '160', label: '160 kbps' },
  { value: '128', label: '128 kbps' },
  { value: '96', label: '96 kbps' },
  { value: '64', label: '64 kbps' },
]

function resolveSizing(value: string): { maxWidth: number | null; maxHeight: number | null } {
  switch (value) {
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

function revokeResultUrls(entry: CompressionResultViewModel) {
  URL.revokeObjectURL(entry.resultObjectUrl)
  if (entry.previewObjectUrl !== entry.resultObjectUrl) {
    URL.revokeObjectURL(entry.previewObjectUrl)
  }
}

function parseNullableInteger(value: string): number | null {
  if (!value.trim()) {
    return null
  }

  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) ? parsed : null
}

function parseTargetSizeBytes(value: string, unit: SizeUnit): number | null {
  if (!value.trim()) {
    return null
  }

  const parsed = Number.parseFloat(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }

  const multiplier = unit === 'MB' ? 1024 * 1024 : 1024
  return Math.round(parsed * multiplier)
}

export function useCompressionWorkspace() {
  const prepared = shallowRef<CompressionPreparedSource | null>(null)
  const resultHistory = ref<CompressionResultViewModel[]>([])
  const selectedResultId = ref<string | null>(null)
  const availableModes = ref<CompressionModeDefinition[]>([])
  const availableTargets = ref<CompressionTargetFormatDefinition[]>([])
  const isLoading = ref(false)
  const isCompressing = ref(false)
  const isCancelling = ref(false)
  const errorMessage = ref('')
  const processingMessage = ref('')
  const compressionAcceptAttribute = ref('')
  const selectedModeId = ref<CompressionModeDefinition['id']>('maximum')
  const selectedTargetExtension = ref('auto')
  const targetSizeValue = ref('')
  const targetSizeUnit = ref<SizeUnit>('MB')
  const selectedResolution = ref('original')
  const quality = ref(0.82)
  const backgroundColor = ref('#fffaf0')
  const selectedTargetFps = ref('')
  const selectedVideoBitrateKbps = ref('')
  const selectedAudioBitrateKbps = ref('')
  const activeJobId = ref('')
  const activeJobStatus = ref<ProcessingJobStatus | ''>('')
  const activeJobProgressPercent = ref(0)
  const lastRequest = shallowRef<CompressionRunRequest | null>(null)
  let capabilityRequest: Promise<void> | null = null

  const activeMode = computed(
    () => availableModes.value.find((mode) => mode.id === selectedModeId.value) ?? null,
  )
  const resolvedTargetExtension = computed(() => {
    if (selectedTargetExtension.value !== 'auto') {
      return selectedTargetExtension.value
    }

    return (
      prepared.value?.source.defaultTargetExtension ?? availableTargets.value[0]?.extension ?? ''
    )
  })
  const activeTarget = computed<CompressionTargetFormatDefinition | null>(
    () =>
      availableTargets.value.find((target) => target.extension === resolvedTargetExtension.value) ??
      null,
  )
  const targetOptions = computed<SelectOption[]>(() => [
    { value: 'auto', label: 'Auto target' },
    ...availableTargets.value.map((target) => ({
      value: target.extension,
      label: target.label,
    })),
  ])
  const result = computed(() => {
    const selectedId = selectedResultId.value
    if (!selectedId) {
      return resultHistory.value[0] ?? null
    }

    return resultHistory.value.find((entry) => entry.id === selectedId) ?? null
  })
  const showTargetSizeControl = computed(() => activeMode.value?.requiresTargetSize ?? false)
  const showManualControls = computed(() => activeMode.value?.supportsCustomSettings ?? false)
  const showResolutionControl = computed(
    () => showManualControls.value && !!activeTarget.value?.supportsResolutionLimits,
  )
  const showQualityControl = computed(
    () => showManualControls.value && !!activeTarget.value?.supportsQuality,
  )
  const showBackgroundColorControl = computed(
    () =>
      showManualControls.value &&
      prepared.value?.source.family === 'image' &&
      !!activeTarget.value &&
      !activeTarget.value.supportsTransparency,
  )
  const showVideoBitrateControl = computed(
    () =>
      showManualControls.value &&
      prepared.value?.source.family === 'media' &&
      !!activeTarget.value?.supportsBitrateControls,
  )
  const showAudioBitrateControl = computed(
    () =>
      showManualControls.value &&
      !!activeTarget.value?.supportsBitrateControls &&
      (prepared.value?.source.family === 'media' || prepared.value?.source.family === 'audio'),
  )
  const showFpsControl = computed(
    () =>
      showManualControls.value &&
      prepared.value?.source.family === 'media' &&
      !!activeTarget.value?.supportsFpsControl,
  )
  const hasResultHistory = computed(() => resultHistory.value.length > 0)
  const canRetry = computed(() => !!lastRequest.value && !isCompressing.value && !isLoading.value)

  watch(activeTarget, (target) => {
    if (!target) {
      return
    }

    if (target.supportsQuality && target.defaultQuality != null) {
      quality.value = target.defaultQuality
    }
  })

  async function hydrateCapabilities(): Promise<void> {
    if (capabilityRequest) {
      return capabilityRequest
    }

    capabilityRequest = Promise.all([
      getCompressionAcceptAttribute(),
      getCompressionModes(),
      getCompressionCapabilityMatrix(),
    ])
      .then(([acceptAttribute, modes, matrix]) => {
        compressionAcceptAttribute.value = acceptAttribute
        availableModes.value = modes
        if (!availableModes.value.some((mode) => mode.id === selectedModeId.value)) {
          selectedModeId.value = availableModes.value[0]?.id ?? 'maximum'
        }
        if (!prepared.value) {
          availableTargets.value = matrix.targetFormats.filter((target) => target.available)
        }
      })
      .finally(() => {
        capabilityRequest = null
      })

    return capabilityRequest
  }

  async function selectFile(file: File): Promise<void> {
    isLoading.value = true
    errorMessage.value = ''
    processingMessage.value = 'Проверяю доступные режимы и форматы сжатия...'

    try {
      await hydrateCapabilities()

      const source = await resolveCompressionSourceFormat(file.name, file.type)
      if (!source || !source.available) {
        throw new Error(
          source?.availabilityDetail || 'Этот файл пока нельзя сжать в текущем окружении.',
        )
      }

      const targets = await listCompressionTargetsForSource(file.name, file.type)
      prepared.value = {
        file,
        source,
        targets,
      }
      availableTargets.value = targets
      selectedTargetExtension.value = 'auto'
      selectedModeId.value = 'maximum'
      targetSizeValue.value = ''
      targetSizeUnit.value = 'MB'
      selectedResolution.value = 'original'
      selectedTargetFps.value = ''
      selectedVideoBitrateKbps.value = ''
      selectedAudioBitrateKbps.value = ''
      quality.value = targets[0]?.defaultQuality ?? 0.82
      backgroundColor.value = '#fffaf0'
      processingMessage.value = ''
    } catch (error) {
      prepared.value = null
      availableTargets.value = []
      errorMessage.value = error instanceof Error ? error.message : 'Не удалось подготовить файл.'
      processingMessage.value = ''
    } finally {
      isLoading.value = false
    }
  }

  function clearSelection(): void {
    prepared.value = null
    availableTargets.value = []
    selectedTargetExtension.value = 'auto'
    targetSizeValue.value = ''
    errorMessage.value = ''
    processingMessage.value = ''
  }

  async function compress(): Promise<void> {
    if (!prepared.value || !activeMode.value) {
      return
    }

    const targetSizeBytes = parseTargetSizeBytes(targetSizeValue.value, targetSizeUnit.value)
    if (activeMode.value.requiresTargetSize && targetSizeBytes == null) {
      errorMessage.value = 'Для режима с лимитом размера укажи положительное значение.'
      return
    }

    const sizing = resolveSizing(selectedResolution.value)
    const request: CompressionRunRequest = {
      file: prepared.value.file,
      mode: activeMode.value.id,
      targetExtension:
        selectedTargetExtension.value === 'auto' ? null : selectedTargetExtension.value,
      targetSizeBytes,
      maxWidth: showResolutionControl.value ? sizing.maxWidth : null,
      maxHeight: showResolutionControl.value ? sizing.maxHeight : null,
      quality: showQualityControl.value ? quality.value : null,
      backgroundColor: backgroundColor.value,
      targetFps: showFpsControl.value ? parseNullableInteger(selectedTargetFps.value) : null,
      videoBitrateKbps: showVideoBitrateControl.value
        ? parseNullableInteger(selectedVideoBitrateKbps.value)
        : null,
      audioBitrateKbps: showAudioBitrateControl.value
        ? parseNullableInteger(selectedAudioBitrateKbps.value)
        : null,
      presetLabel: activeMode.value.label,
    }

    lastRequest.value = request
    errorMessage.value = ''
    isCompressing.value = true
    processingMessage.value = 'Запускаю сжатие...'

    try {
      const response = await runServerCompression({
        ...request,
        reportProgress: (message) => {
          processingMessage.value = message
        },
        onJobCreated: (job) => {
          activeJobId.value = job.id
          activeJobStatus.value = job.status
          activeJobProgressPercent.value = job.progressPercent
        },
        onJobUpdate: (job) => {
          activeJobStatus.value = job.status
          activeJobProgressPercent.value = job.progressPercent
        },
      })

      const resultEntry = createResultEntry(prepared.value, response)
      resultHistory.value = [resultEntry, ...resultHistory.value].slice(0, MAX_RESULT_HISTORY)
      selectedResultId.value = resultEntry.id
      while (resultHistory.value.length > MAX_RESULT_HISTORY) {
        const removed = resultHistory.value.pop()
        if (removed) {
          revokeResultUrls(removed)
        }
      }
      processingMessage.value = response.manifest.targetMet
        ? 'Сжатие завершено и уложилось в выбранный лимит.'
        : 'Сжатие завершено. Сервис сохранил лучший найденный вариант в рамках заданных ограничений.'
    } catch (error) {
      if (error instanceof ProcessingJobCancelledError) {
        processingMessage.value = 'Сжатие остановлено.'
      } else {
        errorMessage.value =
          error instanceof Error ? error.message : 'Сжатие завершилось с ошибкой.'
        processingMessage.value = ''
      }
    } finally {
      isCompressing.value = false
    }
  }

  async function retryLastCompression(): Promise<void> {
    if (!lastRequest.value) {
      return
    }

    await compress()
  }

  async function cancelCompression(): Promise<void> {
    if (!activeJobId.value || isCancelling.value) {
      return
    }

    isCancelling.value = true
    errorMessage.value = ''

    try {
      const cancelledJob = await cancelProcessingJob(activeJobId.value)
      activeJobStatus.value = cancelledJob.status
      activeJobProgressPercent.value = cancelledJob.progressPercent
      processingMessage.value = cancelledJob.message || 'Сжатие остановлено.'
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'Не удалось остановить сжатие.'
    } finally {
      isCancelling.value = false
    }
  }

  function selectResult(id: string): void {
    selectedResultId.value = id
  }

  function downloadResult(entry: CompressionResultViewModel | null = result.value): void {
    if (!entry) {
      return
    }

    downloadBlob(entry.resultObjectUrl, entry.fileName)
  }

  onBeforeUnmount(() => {
    for (const entry of resultHistory.value) {
      revokeResultUrls(entry)
    }
  })

  return {
    prepared,
    result,
    resultHistory,
    availableModes,
    availableTargets,
    activeMode,
    activeTarget,
    targetOptions,
    isLoading,
    isCompressing,
    isCancelling,
    errorMessage,
    processingMessage,
    compressionAcceptAttribute,
    selectedModeId,
    selectedTargetExtension,
    targetSizeValue,
    targetSizeUnit,
    selectedResolution,
    quality,
    backgroundColor,
    selectedTargetFps,
    selectedVideoBitrateKbps,
    selectedAudioBitrateKbps,
    activeJobId,
    activeJobStatus,
    activeJobProgressPercent,
    showTargetSizeControl,
    showManualControls,
    showResolutionControl,
    showQualityControl,
    showBackgroundColorControl,
    showVideoBitrateControl,
    showAudioBitrateControl,
    showFpsControl,
    hasResultHistory,
    canRetry,
    resolutionOptions: RESOLUTION_OPTIONS,
    fpsOptions: FPS_OPTIONS,
    videoBitrateOptions: VIDEO_BITRATE_OPTIONS,
    audioBitrateOptions: AUDIO_BITRATE_OPTIONS,
    selectFile,
    clearSelection,
    compress,
    retryLastCompression,
    cancelCompression,
    selectResult,
    downloadResult,
  }
}

function createResultEntry(
  prepared: CompressionPreparedSource,
  response: Awaited<ReturnType<typeof runServerCompression>>,
): CompressionResultViewModel {
  const resultObjectUrl = URL.createObjectURL(response.resultBlob)
  const previewObjectUrl = URL.createObjectURL(response.previewBlob)
  const reductionPercent =
    prepared.file.size > 0
      ? ((prepared.file.size - response.manifest.resultSizeBytes) / prepared.file.size) * 100
      : 0

  return {
    id: crypto.randomUUID(),
    sourceFileName: prepared.file.name,
    fileName: response.resultArtifact.fileName,
    family: response.manifest.family,
    mode: response.manifest.mode,
    targetMet: response.manifest.targetMet,
    sourceSizeBytes: response.manifest.sourceSizeBytes,
    resultSizeBytes: response.manifest.resultSizeBytes,
    reductionPercent,
    warnings: response.manifest.warnings,
    sourceFacts: response.manifest.sourceFacts,
    resultFacts: response.manifest.resultFacts,
    compressionFacts: response.manifest.compressionFacts,
    attempts: response.manifest.attempts,
    backendJobId: response.job.id,
    backendRuntimeLabel: response.manifest.runtimeLabel,
    createdAt: response.job.completedAt || new Date().toISOString(),
    resultObjectUrl,
    previewObjectUrl,
    previewKind: response.manifest.previewKind,
    previewMimeType: response.manifest.previewMediaType,
    resultMimeType: response.manifest.resultMediaType,
  }
}

function downloadBlob(objectUrl: string, fileName: string): void {
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  anchor.rel = 'noopener'
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
}
