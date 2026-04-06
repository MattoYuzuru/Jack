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
  cacheKey: string
}

const MAX_RESULT_HISTORY = 8
const converterRuntime = createConverterRuntime()

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
  })

  function buildCacheKey(request: {
    prepared: ConverterPreparedSource
    targetExtension: string
    presetId: string
    quality: number
    backgroundColor: string
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
      'Переиспользую уже собранный backend artifact из текущей сессии без повторного IMAGE_CONVERT job.'
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

    return {
      prepared: prepared.value,
      targetExtension: selectedTargetExtension.value,
      presetId: selectedPresetId.value,
      quality: quality.value,
      backgroundColor: backgroundColor.value,
      cacheKey: buildCacheKey({
        prepared: prepared.value,
        targetExtension: selectedTargetExtension.value,
        presetId: selectedPresetId.value,
        quality: quality.value,
        backgroundColor: backgroundColor.value,
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

    await runConversion(lastRequest.value)
  }

  async function requestCancellation(options: { silent?: boolean } = {}) {
    if (!activeJobId.value || !isConverting.value) {
      return
    }

    isCancelling.value = true
    if (!options.silent) {
      processingMessage.value = 'Отправляю запрос на отмену backend IMAGE_CONVERT job...'
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
